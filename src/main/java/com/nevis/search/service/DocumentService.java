package com.nevis.search.service;

import com.nevis.search.model.Document;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface DocumentService {
    Document ingestDocument(String title, String content, UUID clientId);
    void saveEmbeddings(UUID docId, UUID chunkId, Map<String, float[]> embeddingMap);
    List<DocumentSearchResult> search(float[] queryVector, int limit, Optional<UUID> clientId);
}