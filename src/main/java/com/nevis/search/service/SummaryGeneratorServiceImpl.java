package com.nevis.search.service;

import com.nevis.search.exception.EntityNotFoundException;
import com.nevis.search.infra.RateLimiter;
import com.nevis.search.model.DocumentTaskStatus;
import com.nevis.search.repository.DocumentRepository;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.nevis.search.service.EmbeddingServiceImpl.CHAT_LIMIT;

@Service
@Slf4j
public class SummaryGeneratorServiceImpl implements SummaryGeneratorService {

    private final DocumentRepository documentRepository;
    private final ChatModel chatModel;
    private final RateLimiter chatLimiter;

    @Value("${app.summary.max-chars:200000}")
    private int maxSummaryChars;

    @Value("${app.worker.summary.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.worker.summary.stale-threshold-minutes:5}")
    private int staleThresholdMinutes;

    private static final String SUMMARY_PROMPT_TEMPLATE =
        """
            Act as an expert Wealth Management Analyst. Describe the provided document to be used as a concise CRM entry.
            
            Constraint: Keep the entire description under 2-3 sentences. Use a professional, objective tone
            
            NO Personal Data: Do not extract actual names, account numbers, or specific dollar amounts.
            
            Output: Only summary itself
            
            Document:
            %s
            """;

    public SummaryGeneratorServiceImpl(
        DocumentRepository documentRepository,
        ChatModel chatModel,
        @Qualifier("chatLimiter") RateLimiter chatLimiter
    ) {
        this.documentRepository = documentRepository;
        this.chatModel = chatModel;
        this.chatLimiter = chatLimiter;
    }

    @Override
    public void generateSummary(UUID docId) {
        log.info("Starting summary generation for doc: {}", docId);

        documentRepository.claimForSummary(docId, maxAttempts)
            .ifPresent(document -> {
                try {
                    String content = document.content();
                    if (content == null || content.isBlank()) {
                        log.warn("Doc {}: content is empty, skipping summary", docId);
                        documentRepository.updateSummaryStatus(docId, DocumentTaskStatus.READY, "Empty content");
                        return;
                    }

                    String headContent = content.substring(0, Math.min(content.length(), maxSummaryChars));

                    String summary = chatLimiter.execute(CHAT_LIMIT, 1, () ->
                        chatModel.chat(String.format(SUMMARY_PROMPT_TEMPLATE, headContent))
                    );

                    documentRepository.updateSummary(docId, summary, DocumentTaskStatus.READY);
                    log.info("Summary successfully generated for doc: {}", docId);

                } catch (Exception e) {
                    log.error("FAILED to generate summary for doc {}: {}", docId, e.getMessage(), e);
                    documentRepository.updateSummaryStatus(docId, DocumentTaskStatus.FAILED, e.getMessage());
                }
            });
    }
}