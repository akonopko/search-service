package com.nevis.search.service;

import java.util.UUID;

public interface SummaryGeneratorService {
    void generateSummary(UUID docId);
}