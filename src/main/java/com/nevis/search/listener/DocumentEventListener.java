package com.nevis.search.listener;

import com.nevis.search.event.DocumentIngestedEvent;
import com.nevis.search.event.DocumentRetryEvent;
import com.nevis.search.service.EmbeddingService;
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

    @Async("embeddingTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDocumentIngested(DocumentIngestedEvent event) {
        log.info("Received DocumentIngestedEvent for document: {}", event.documentId());
        embeddingService.generateForDocument(event.documentId());
    }

    @Async("embeddingTaskExecutor")
    @EventListener
    public void handleRetry(DocumentRetryEvent event) {
        embeddingService.generateForDocument(event.documentId());
    }

}