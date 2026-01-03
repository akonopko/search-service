package com.nevis.search.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class DocumentNotFoundException extends RuntimeException {
    private final UUID documentId;

    public DocumentNotFoundException(UUID documentId) {
        super("Document not found: " + documentId);
        this.documentId = documentId;
    }
}