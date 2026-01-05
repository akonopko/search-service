package com.nevis.search.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nevis.search.model.DocumentTaskStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentSearchResultItem(
    @JsonProperty("document_id") UUID documentId,
    @JsonProperty("client_id") UUID clientId,
    String title,
    double score,
    String summary,
    DocumentTaskStatus status,
    @JsonProperty("created_at") OffsetDateTime createdAt
) {
    public DocumentSearchResultItem {
        if (score < 0 || score > 1.000001) {
            throw new IllegalArgumentException("Invalid similarity score: " + score);
        }
    }
}