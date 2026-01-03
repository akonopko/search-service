package com.nevis.search.service;

import com.nevis.search.exception.EmbeddingMismatchException;
import com.nevis.search.model.DocumentChunk;
import com.nevis.search.model.DocumentTaskStatus;
import com.nevis.search.repository.DocumentRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingServiceImpl implements EmbeddingService {

    private final DocumentService documentService;
    private final DocumentRepository repository;
    private final EmbeddingModel embeddingModel;

    @Retryable(
        retryFor = {
            dev.langchain4j.exception.RetriableException.class,
            EmbeddingMismatchException.class,
            TransientDataAccessException.class
        },
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2.0)
    )
    public void generateForDocument(UUID docId) {
        List<DocumentChunk> pendingChunks = repository.findChunksByStatusDocId(docId, DocumentTaskStatus.PENDING);

        if (pendingChunks.isEmpty()) {
            return;
        }

        List<TextSegment> segments = pendingChunks.stream()
            .map(chunk -> TextSegment.from(chunk.content()))
            .toList();

        validateSegments(segments, docId);

        Response<List<Embedding>> response = embeddingModel.embedAll(segments);
        List<Embedding> embeddings = response.content();

        if (embeddings.size() != pendingChunks.size()) {
            String sampleText = segments.isEmpty() ? "No segments" : segments.get(0).text();
            String safeSample = sampleText.length() > 30
                ? sampleText.substring(0, 30) + "..."
                : sampleText;

            log.error("Mismatch for doc {}: Expected {}, Got {}. Sample text: {}",
                docId, pendingChunks.size(), embeddings.size(), safeSample);

            throw new EmbeddingMismatchException("Provider returned mismatched count");
        }

        Map<UUID, float[]> embeddingMap = IntStream.range(0, pendingChunks.size())
            .boxed()
            .collect(Collectors.toMap(i -> pendingChunks.get(i).id(), i -> embeddings.get(i).vector()));

        documentService.saveEmbeddings(docId, embeddingMap);
    }

    private void validateSegments(List<TextSegment> segments, UUID docId) {
        if (segments == null || segments.isEmpty()) {
            throw new IllegalArgumentException("No segments found for document: " + docId);
        }

        for (int i = 0; i < segments.size(); i++) {
            String text = segments.get(i).text();

            if (text == null || text.trim().isEmpty()) {
                throw new IllegalArgumentException(
                    String.format("Segment at index %d for doc %s is empty or null.", i, docId)
                );
            }

            // 2. Check for "Too Long" segments (Token Limits)
            if (text.length() > 30000) {
                log.warn("Segment {} in doc {} is very large ({} chars). This might fail at the AI provider.",
                    i, docId, text.length());
            }
        }
    }

    @Recover
    public void recover(Exception e, UUID docId) {
        log.error("Permanently failed to process document {}. Error: {}", docId, e.getMessage());
        repository.updateEmbeddingStatus(docId, DocumentTaskStatus.FAILED, e.getMessage());
    }

}