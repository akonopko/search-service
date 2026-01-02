package com.nevis.search.repository;

import com.nevis.search.model.Client;
import com.nevis.search.model.Document;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcDocumentRepository implements DocumentRepository {

    @Override
    public Document save(Document document) {
        return null;
    }

    @Override
    public Optional<Document> findById(UUID id) {
        return null;
    }

}