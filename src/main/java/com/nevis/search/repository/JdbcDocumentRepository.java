package com.nevis.search.repository;

import com.nevis.search.exception.EntityNotFoundException;
import com.nevis.search.model.Document;
import com.nevis.search.model.DocumentChunk;
import com.nevis.search.model.DocumentTaskStatus;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

    private final RowMapper<Document> documentRowMapper = (rs, rowNum) -> {
        String sumStatusStr = rs.getString("summary_status");
        DocumentTaskStatus summaryStatus = (sumStatusStr != null)
            ? DocumentTaskStatus.valueOf(sumStatusStr)
            : DocumentTaskStatus.PENDING;

        String mainStatusStr = rs.getString("status");
        DocumentTaskStatus mainStatus = (mainStatusStr != null)
            ? DocumentTaskStatus.valueOf(mainStatusStr)
            : DocumentTaskStatus.PENDING;

        return new Document(
            rs.getObject("id", UUID.class),
            rs.getObject("client_id", UUID.class),
            rs.getString("title"),
            rs.getString("content"),
            rs.getString("summary"),
            summaryStatus,
            rs.getString("summary_error_message"),
            rs.getInt("summary_attempts"),
            mainStatus,
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
        );
    };

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

    @Override
    @Transactional
    public void updateSummaryStatus(UUID id, DocumentTaskStatus status, String error) {
        String sql = """
            UPDATE documents 
            SET summary_status = :status::task_status,
                summary_error_message = :error,
                updated_at = NOW()
            WHERE id = :id
            """;

        int rowsAffected = jdbcClient.sql(sql)
            .param("status", status.name())
            .param("error", error)
            .param("id", id)
            .update();

        if (rowsAffected == 0) {
            throw new EntityNotFoundException(id);
        }
    }

    @Override
    @Transactional
    public void updateSummary(UUID id, String summary, DocumentTaskStatus status) {
        String sql = """
            UPDATE documents 
            SET summary = :summary,
                summary_status = :status::task_status,
                summary_error_message = NULL,
                updated_at = NOW()
            WHERE id = :id
            """;

        int rowsAffected = jdbcClient.sql(sql)
            .param("summary", summary)
            .param("status", status.name())
            .param("id", id)
            .update();

        if (rowsAffected == 0) {
            throw new EntityNotFoundException(id);
        }
    }

    @Transactional
    @Override
    public Optional<Document> claimForSummary(UUID docId, int maxAttempts) {
        String sql = """
            UPDATE documents 
            SET summary_status = 'PROCESSING'::task_status, 
                updated_at = NOW()
            WHERE id = (
                SELECT id FROM documents 
                WHERE id = :docId 
                  AND summary_status = 'PENDING'::task_status
                  AND summary_attempts < :maxAttempts
                FOR UPDATE SKIP LOCKED
            )
            RETURNING *
            """;

        return jdbcClient.sql(sql)
            .param("docId", docId)
            .param("maxAttempts", maxAttempts)
            .query(documentRowMapper)
            .optional();
    }

    @Override
    @Transactional
    public List<UUID> resetStaleAndFailedSummaries(int maxAttempts, int staleThresholdMinutes) {
        String sql = """
        UPDATE documents 
        SET summary_status = 'PENDING'::task_status,
            summary_attempts = summary_attempts + 1, 
            updated_at = NOW()
        WHERE id IN (
            SELECT id 
            FROM documents
            WHERE (
                summary_status = 'FAILED'::task_status 
                OR (summary_status = 'PROCESSING'::task_status AND updated_at < NOW() - (INTERVAL '1 minute' * :staleMins))
            )
            AND summary_attempts < :maxAttempts
            FOR UPDATE SKIP LOCKED
        )
        RETURNING id        
        """;

        return jdbcClient.sql(sql)
            .param("maxAttempts", maxAttempts)
            .param("staleMins", staleThresholdMinutes)
            .query(UUID.class)
            .list();
    }

}