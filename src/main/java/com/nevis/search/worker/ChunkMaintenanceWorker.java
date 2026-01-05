package com.nevis.search.worker;

import com.nevis.search.event.DocumentEmbeddingsRetryEvent;
import com.nevis.search.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChunkMaintenanceWorker {

    private final DocumentChunkRepository chunkRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.worker.embeddings.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.worker.embeddings.stale-threshold-minutes:5}")
    private int staleThresholdMinutes;

    @Scheduled(fixedDelayString = "${app.worker.cleanup-interval-ms:60000}")
    @Transactional
    public void cleanupStaleChunks() {
        log.debug("Starting maintenance: checking for failed or stuck chunks...");

        List<UUID> affectedDocIds = chunkRepository.resetStaleAndFailedChunks(maxAttempts, staleThresholdMinutes);

        if (affectedDocIds.isEmpty()) {
            return;
        }

        Set<UUID> uniqueDocIds = new HashSet<>(affectedDocIds);

        log.info("Maintenance recovered {} chunks across {} documents. Re-triggering processing...", 
                 affectedDocIds.size(), uniqueDocIds.size());

        uniqueDocIds.forEach(docId ->
            eventPublisher.publishEvent(new DocumentEmbeddingsRetryEvent(docId))
        );
    }
}