package com.nevis.search.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Document(
    UUID id,
    UUID clientId,
    String title,
    String content,
    String summary,
    DocumentTaskStatus summaryStatus,
    String summaryErrorMessage,
    int summaryAttempts,
    DocumentTaskStatus status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}