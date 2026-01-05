package com.nevis.search.repository;

import com.nevis.search.model.Document;
import com.nevis.search.model.DocumentTaskStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository {
    Document save(Document document);
    Optional<Document> findById(UUID id);
    void updateStatus(UUID id, DocumentTaskStatus status);
    void updateSummaryStatus(UUID id, DocumentTaskStatus status, String error);
    void updateSummary(UUID id, String summary, DocumentTaskStatus status);
    Optional<Document> claimForSummary(UUID docId, int maxAttempts);
    List<UUID> resetStaleAndFailedSummaries(int maxAttempts, int staleMinutes);
}