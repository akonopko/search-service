package com.nevis.search.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nevis.search.model.DocumentTaskStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentResponse(
    UUID id,
    
    @JsonProperty("client_id")
    UUID clientId,
    
    String title,
    
    String content,
    
    String summary,
    
    DocumentTaskStatus status,
    
    @JsonProperty("created_at")
    OffsetDateTime createdAt
) {}