package com.nevis.search.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Document(
	UUID id,
	UUID clientId,
	String title,
	String content,
	String summary,
	DocumentStatus status,
	OffsetDateTime createdAt,
	OffsetDateTime updatedAt
) {}