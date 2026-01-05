package com.nevis.search.repository;

import com.nevis.search.exception.EntityNotFoundException;
import com.nevis.search.model.Document;
import com.nevis.search.model.DocumentChunk;
import com.nevis.search.model.DocumentTaskStatus;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcDocumentRepository implements DocumentRepository {

    private final JdbcClient jdbcClient;

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Document> documentRowMapper = (rs, rowNum) -> new Document(
        rs.getObject("id", UUID.class),
        rs.getObject("client_id", UUID.class),
        rs.getString("title"),
        rs.getString("content"),
        rs.getString("summary"),
        DocumentTaskStatus.valueOf(rs.getString("status")),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class)
    );

    private final RowMapper<DocumentChunk> documentChunkMapper = (rs, rowNum) -> new DocumentChunk(
        rs.getObject("id", UUID.class),
        rs.getObject("document_id", UUID.class),
        rs.getString("content"),
        rs.getString("chunk_summary"),
        DocumentTaskStatus.valueOf(rs.getString("status")),
        rs.getString("error_message"),
        rs.getInt("attempts"),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class)
    );

    @Override
    public Document save(Document document) {
        return jdbcClient.sql("""
                INSERT INTO documents (client_id, title, content, summary, status)
                VALUES (:clientId, :title, :content, :summary, :status::task_status)
                RETURNING *
                """)
            .param("clientId", document.clientId())
            .param("title", document.title())
            .param("content", document.content())
            .param("summary", document.summary())
            .param("status", document.status() != null ? document.status().name() : DocumentTaskStatus.PENDING.name())
            .query(documentRowMapper)
            .single();
    }

    public void saveChunks(UUID docId, List<TextSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return;
        }

        String sql = """
                INSERT INTO document_chunks (document_id, content, status) 
                VALUES (?, ?, 'PENDING'::task_status)
            """;

        jdbcTemplate.batchUpdate(sql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
            @Override
            @SneakyThrows
            public void setValues(PreparedStatement ps, int i) {
                TextSegment segment = segments.get(i);
                ps.setObject(1, docId);
                ps.setString(2, segment.text());
            }

            @Override
            public int getBatchSize() {
                return segments.size();
            }
        });
    }

    @Override
    public Optional<Document> findById(UUID id) {
        return jdbcClient.sql("SELECT * FROM documents WHERE id = :id")
            .param("id", id)
            .query(documentRowMapper)
            .optional();
    }

    @Override
    public void updateStatus(UUID id, DocumentTaskStatus status) {
        String sql = """
            UPDATE documents 
            SET status = :status::task_status
            WHERE id = :id
            """;

        int rowsAffected = jdbcClient.sql(sql)
            .param("status", status.name())
            .param("id", id)
            .update();

        if (rowsAffected == 0) {
            throw new EntityNotFoundException(id);
        }
    }

}