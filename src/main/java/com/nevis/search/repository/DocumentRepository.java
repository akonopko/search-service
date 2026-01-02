package com.nevis.search.repository;

import com.nevis.search.model.Document;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository {
    Document save(Document document);
    Optional<Document> findById(UUID id);
}