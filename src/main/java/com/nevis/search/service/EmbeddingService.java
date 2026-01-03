package com.nevis.search.service;

import java.util.UUID;

public interface EmbeddingService {
    void generateForDocument(UUID docId);
}