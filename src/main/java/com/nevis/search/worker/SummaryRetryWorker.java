package com.nevis.search.worker;

import com.nevis.search.event.DocumentSummaryRetryEvent;
import com.nevis.search.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class SummaryRetryWorker {

    private final DocumentRepository documentRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.worker.summary.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.worker.summary.stale-threshold-minutes:5}")
    private int staleThresholdMinutes;

    @Scheduled(fixedDelayString = "${app.summary.retry-check-interval-ms:60000}")
    public void retrySummaries() {
        log.debug("Checking for failed or stale summaries...");

        List<UUID> docIds = documentRepository.resetStaleAndFailedSummaries(maxAttempts, staleThresholdMinutes);

        if (!docIds.isEmpty()) {
            log.info("Resetting {} documents for summary retry", docIds.size());
            docIds.forEach(id -> eventPublisher.publishEvent(new DocumentSummaryRetryEvent(id)));
        }
    }
}