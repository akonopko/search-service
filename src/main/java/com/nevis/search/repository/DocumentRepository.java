package com.nevis.search.repository;

import com.nevis.search.model.Document;
import com.nevis.search.model.DocumentChunk;
import com.nevis.search.model.DocumentTaskStatus;
import dev.langchain4j.data.segment.TextSegment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository {
    Document save(Document document);
    void saveChunks(UUID docId, List<TextSegment> segments);
    Optional<Document> findById(UUID id);
    List<DocumentChunk> findChunksByStatusDocId(UUID id, DocumentTaskStatus status);
    boolean areAllChunksProcessed(UUID docId);
    void updateDocumentStatus(UUID id, DocumentTaskStatus status);
    void updateEmbeddingStatus(UUID id, DocumentTaskStatus status);
    void updateEmbeddingStatus(UUID id, DocumentTaskStatus status, String error);
    void updateEmbeddingChunkVector(UUID id, float[] vector);
}