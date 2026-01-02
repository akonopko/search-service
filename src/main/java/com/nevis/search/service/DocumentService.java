package com.nevis.search.service;

import com.nevis.search.model.Document;

import java.util.UUID;

public interface DocumentService {
    Document ingestDocument(String title, String content, UUID clientId);
}