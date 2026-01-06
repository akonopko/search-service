package com.nevis.search.service;

import com.nevis.search.controller.ClientResponse;
import com.nevis.search.controller.DocumentResponse;
import com.nevis.search.model.Document;
import com.nevis.search.controller.DocumentSearchResultItem;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface DocumentService {
    DocumentResponse ingestDocument(String title, String content, UUID clientId);
    void saveEmbeddings(UUID docId, UUID chunkId, Map<String, float[]> embeddingMap);
    List<DocumentSearchResultItem> search(float[] queryVector, Optional<Integer> limit, Optional<UUID> clientId);
    DocumentResponse getById(UUID id);
}