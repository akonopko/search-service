package com.nevis.search.repository;

import com.nevis.search.model.Document;
import dev.langchain4j.data.segment.TextSegment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository {
    Document save(Document document);
    void saveChunks(UUID docId, List<TextSegment> segments);
    Optional<Document> findById(UUID id);
}