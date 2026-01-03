package com.nevis.search.exception;

import lombok.Getter;
import lombok.experimental.StandardException;

import java.util.UUID;

@Getter
public class ChunkNotFoundException extends RuntimeException {
    private final UUID chunkId;

    public ChunkNotFoundException(UUID chunkId) {
        super("Chunk not found: " + chunkId);
        this.chunkId = chunkId;
    }
}