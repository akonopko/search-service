package com.nevis.search.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class EntityNotFoundException extends RuntimeException {
    private final UUID chunkId;

    public EntityNotFoundException(UUID chunkId) {
        super("Entity not found: " + chunkId);
        this.chunkId = chunkId;
    }
}