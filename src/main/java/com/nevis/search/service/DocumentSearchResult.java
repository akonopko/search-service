package com.nevis.search.service;

import java.util.UUID;

public record DocumentSearchResult(
    UUID documentId,
    String title,
    String content,
    double score
) {
    public DocumentSearchResult {
        if (score < 0 || score > 1.000001) {
            throw new IllegalArgumentException("Invalid similarity score: " + score);
        }
    }
}