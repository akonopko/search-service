package com.nevis.search.listener;

import com.nevis.search.event.DocumentIngestedEvent;
import com.nevis.search.event.DocumentEmbeddingsRetryEvent;
import com.nevis.search.event.DocumentSummaryRetryEvent;
import com.nevis.search.service.EmbeddingService;
import com.nevis.search.service.SummaryGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class DocumentEventListener {

    private final EmbeddingService embeddingService;
    private final SummaryGeneratorService summaryGeneratorService;

    @Async("embeddingTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleEmbeddingTask(DocumentIngestedEvent event) {
        log.info("Starting Async Embedding for doc: {}", event.documentId());
        embeddingService.generateForDocument(event.documentId());
    }

    @Async("embeddingTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSummaryTask(DocumentIngestedEvent event) {
        log.info("Starting Async Summary for doc: {}", event.documentId());
        summaryGeneratorService.generateSummary(event.documentId());
    }

    @Async("embeddingTaskExecutor")
    @EventListener
    public void handleRetry(DocumentEmbeddingsRetryEvent event) {
        embeddingService.generateForDocument(event.documentId());
    }

    @Async("embeddingTaskExecutor")
    @EventListener
    public void handleSummaryRetry(DocumentSummaryRetryEvent event) {
        summaryGeneratorService.generateSummary(event.documentId());
    }


}