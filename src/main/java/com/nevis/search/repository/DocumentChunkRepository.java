package com.nevis.search.repository;

import com.nevis.search.model.DocumentChunk;
import com.nevis.search.model.DocumentTaskStatus;
import dev.langchain4j.data.segment.TextSegment;

import java.util.List;
import java.util.UUID;

public interface DocumentChunkRepository {
    void saveChunks(UUID docId, List<TextSegment> segments);
    List<DocumentChunk> startProcessing(UUID id);
    boolean areAllChunksProcessed(UUID docId);
    void updateStatus(UUID id, DocumentTaskStatus status);
    void markAllDocumentChunksAsFailed(UUID id, String error);
    void updateVector(UUID id, float[] vector);
}