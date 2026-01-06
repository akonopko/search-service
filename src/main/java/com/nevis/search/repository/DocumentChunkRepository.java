package com.nevis.search.repository;

import com.nevis.search.model.DocumentChunk;
import com.nevis.search.model.DocumentTaskStatus;
import com.nevis.search.controller.DocumentSearchResultItem;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentChunkRepository {
    void saveChunks(UUID docId, List<TextSegment> segments);
    int countPendingByDocumentId(UUID docId);
    Optional<DocumentChunk> claimNextPendingChunk(UUID docId, int maxAttempts);
    boolean areAllChunksProcessed(UUID docId);
    void updateStatus(UUID chunkId, DocumentTaskStatus status);
    void markAsFailed(UUID id, String error);
    void insertChunkVector(UUID docId, UUID chunkId, String content, float[] vector);
    List<DocumentSearchResultItem> findSimilar(float[] vector, Optional<Integer> limit, Optional<UUID> clientId, double threshold);
    List<UUID> resetStaleAndFailedChunks(int maxAttempts, int staleThresholdMinutes);
}