package com.nevis.search.event;

import java.util.UUID;

public record DocumentEmbeddingsRetryEvent(UUID documentId) {}