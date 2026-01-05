package com.nevis.search.repository;

import com.nevis.search.model.DocumentChunk;
import com.nevis.search.model.DocumentTaskStatus;
import com.nevis.search.service.DocumentSearchResult;
import dev.langchain4j.data.segment.TextSegment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentChunkRepository {
    void saveChunks(UUID docId, List<TextSegment> segments);
    List<DocumentChunk> startProcessing(UUID id);
    boolean areAllChunksProcessed(UUID docId);
    void updateStatus(UUID chunkId, DocumentTaskStatus status);
    void markAllDocumentChunksAsFailed(UUID id, String error);
    void insertChunkVector(UUID docId, UUID chunkId, String content, float[] vector);
}