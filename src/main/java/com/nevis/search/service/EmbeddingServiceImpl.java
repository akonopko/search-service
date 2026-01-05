package com.nevis.search.service;

import com.nevis.search.exception.EmbeddingException;
import com.nevis.search.infra.RateLimiter;
import com.nevis.search.model.DocumentChunk;
import com.nevis.search.model.DocumentTaskStatus;
import com.nevis.search.repository.DocumentChunkRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Slf4j
public class EmbeddingServiceImpl implements EmbeddingService {

    public static final String CHAT_LIMIT = "chat_limit";
    public static final String EMBEDDING_LIMIT = "embedding_limit";

    private final DocumentService documentService;
    private final DocumentChunkRepository chunkRepository;
    private final EmbeddingModel embeddingModel;
    private final ChatModel chatModel;
    private final RateLimiter chatLimiter;
    private final RateLimiter embeddingLimiter;

    @Value("${app.worker.embeddings.max-attempts:5}")
    private int maxAttempts;

    private static final String SUMMARY_PROMPT_TEMPLATE =
        """            
            Role: You are an expert Data Architect for a Global Wealth Management firm. Your goal is to generate a comprehensive metadata tag cloud for any document provided.
            
            Task: Analyze the provided text and extract a comma-separated list of meaningful industry terms that are mentioned in document and should be used to tag this file in a CRM.
            
            Extraction Logic:
            
            Regulatory & Compliance: What does this prove for KYC, AML, or tax purposes? (e.g., Source of Wealth, Tax Residency, Identity Verification).
            
            Financial Concepts: What asset classes, instruments, or strategies are mentioned or implied? (e.g., Fixed Income, Alternative Investments, Cost-Basis Reporting).
            
            Document Equivalents: What are the industry synonyms for this document type? (e.g., if it's a 1040, include Tax Return, Income Disclosure, Fiscal Filing).
            
            Strict Constraints:
            
            NO Personal Data: Do not extract actual names, account numbers, or specific dollar amounts.
            
            Terms Only: Output only a flat, comma-separated list of professional terms
            
            Input Text: %s
            """;

    public EmbeddingServiceImpl(
        @Qualifier("chatLimiter") RateLimiter chatLimiter,
        @Qualifier("embeddingLimiter") RateLimiter embeddingLimiter,
        DocumentService documentService,
        DocumentChunkRepository chunkRepository,
        EmbeddingModel embeddingModel,
        ChatModel chatModel
    ) {
        this.chatLimiter = chatLimiter;
        this.embeddingLimiter = embeddingLimiter;
        this.documentService = documentService;
        this.chunkRepository = chunkRepository;
        this.embeddingModel = embeddingModel;
        this.chatModel = chatModel;
    }

    public void generateForDocument(UUID docId) {
        int totalPending = chunkRepository.countPendingByDocumentId(docId);
        log.info("Found {} pending chunks for document {}", totalPending, docId);

        for (int i = 0; i < totalPending; i++) {
            chunkRepository
                .claimNextPendingChunk(docId, maxAttempts)
                .ifPresent(chunk -> processSingleChunk(docId, chunk));
        }
    }

    private void processSingleChunk(UUID docId, DocumentChunk chunk) {
        try {
            List<String> terms = chatLimiter.execute(CHAT_LIMIT, 1, () -> getChunkTerms(chunk));

            if (terms.isEmpty()) {
                chunkRepository.updateStatus(chunk.id(), DocumentTaskStatus.READY);
                return;
            }

            int estimatedTokens = terms.stream().mapToInt(String::length).sum() / 4;
            Response<List<Embedding>> response = embeddingLimiter.execute(EMBEDDING_LIMIT, estimatedTokens,
                () -> embeddingModel.embedAll(
                    terms.stream().map(TextSegment::from).toList()
                ));

            Map<String, float[]> embeddingMap = IntStream.range(0, terms.size())
                .boxed()
                .collect(Collectors.toMap(
                    terms::get,
                    i -> response.content().get(i).vector()
                ));

            documentService.saveEmbeddings(docId, chunk.id(), embeddingMap);

        } catch (Exception e) {
            log.error("Error on chunk {}: {}", chunk.id(), e.getMessage());
            chunkRepository.markAsFailed(chunk.id(), e.getMessage());
        }
    }

    @Override
    public float[] embedQuery(String inputQuery) {
        if (inputQuery == null || inputQuery.isBlank()) {
            throw new IllegalArgumentException("Query cannot be empty");
        }

        String query = inputQuery.trim().toLowerCase();
        if (query.length() > 1000) {
            query = query.substring(0, 1000);
            log.warn("Query was truncated for embedding: {}", query);
        }

        log.debug("Generating embedding for query: '{}'", query);

        try {
            float[] vector = embeddingModel.embed(query).content().vector();
            if (vector == null || vector.length == 0) {
                throw new IllegalStateException("Embedding model returned an empty vector for query: " + query);
            }

            return vector;

        } catch (Exception e) {
            log.error("Failed to generate embedding for query: {}", query, e);
            throw new EmbeddingException("Error during query vectorization", e);
        }
    }

    private List<String> getChunkTerms(DocumentChunk chunk) {
        String termsList = chatModel.chat(String.format(SUMMARY_PROMPT_TEMPLATE, chunk.content()));
        if (termsList.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(termsList.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .collect(Collectors.toList());
    }

}