package com.nevis.search.model;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record DocumentChunk(
    UUID id,
    UUID documentId,
    String content,
    String chunkSummary,
    DocumentTaskStatus status,
    String errorMessage,
    int attempts,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {

}