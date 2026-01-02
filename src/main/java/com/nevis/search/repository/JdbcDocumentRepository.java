package com.nevis.search.repository;

import com.nevis.search.model.Document;
import com.nevis.search.model.DocumentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcDocumentRepository implements DocumentRepository {

    private final JdbcClient jdbcClient;

    private final RowMapper<Document> documentRowMapper = (rs, rowNum) -> new Document(
        rs.getObject("id", UUID.class),
        rs.getObject("client_id", UUID.class),
        rs.getString("title"),
        rs.getString("content"),
        rs.getString("summary"),
        DocumentStatus.valueOf(rs.getString("status")),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class)
    );

    @Override
    @Transactional
    public Document save(Document document) {
        return jdbcClient.sql("""
                INSERT INTO documents (client_id, title, content, summary, status)
                VALUES (:clientId, :title, :content, :summary, :status)
                RETURNING *
                """)
            .param("clientId", document.clientId())
            .param("title", document.title())
            .param("content", document.content())
            .param("summary", document.summary())
            .param("status", document.status() != null ? document.status().name() : DocumentStatus.PENDING.name())
            .query(documentRowMapper)
            .single();
    }

    @Override
    public Optional<Document> findById(UUID id) {
        return jdbcClient.sql("SELECT * FROM documents WHERE id = :id")
            .param("id", id)
            .query(documentRowMapper)
            .optional();
    }

}