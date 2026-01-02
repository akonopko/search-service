package com.nevis.search.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record Client(
	UUID id,
	String firstName,
	String lastName,
	String email,
	String description,
	List<String> socialLinks,
	OffsetDateTime createdAt,
	OffsetDateTime updatedAt
) {}