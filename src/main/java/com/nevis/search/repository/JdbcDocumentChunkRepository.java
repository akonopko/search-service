package com.nevis.search.repository;

import com.nevis.search.exception.EntityNotFoundException;
import com.nevis.search.model.DocumentChunk;
import com.nevis.search.model.DocumentTaskStatus;
import com.nevis.search.controller.DocumentSearchResultItem;
import com.pgvector.PGvector;
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
public class JdbcDocumentChunkRepository implements DocumentChunkRepository {

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.worker.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.worker.stale-threshold-minutes:5}")
    private int staleThresholdMinutes;

    @Value("${app.document.similarity-threshold:0.5}")
    private double documentSimilarityThreshold;

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
    @Transactional
    public Optional<DocumentChunk> claimNextPendingChunk(UUID docId) {
        String sql = """
            UPDATE document_chunks 
            SET status = 'PROCESSING'::task_status, updated_at = NOW()
            WHERE id = (
                SELECT id FROM document_chunks 
                WHERE document_id = :docId 
                  AND status = 'PENDING'::task_status
                  AND attempts < :maxAttempts
                ORDER BY created_at ASC 
                LIMIT 1 FOR UPDATE SKIP LOCKED
            )
            RETURNING *
            """;

        return jdbcClient.sql(sql)
            .param("docId", docId)
            .param("maxAttempts", maxAttempts)
            .query(documentChunkMapper)
            .optional();
    }

    @Override
    public int countPendingByDocumentId(UUID docId) {
        String sql = """
            SELECT COUNT(*) 
            FROM document_chunks 
            WHERE document_id = :docId 
              AND status = 'PENDING'::task_status
            """;

        return jdbcClient.sql(sql)
            .param("docId", docId)
            .query(Integer.class)
            .single();
    }

    @Override
    public boolean areAllChunksProcessed(UUID docId) {
        String sql = """
            SELECT COUNT(*) 
            FROM document_chunks 
            WHERE document_id = :docId 
              AND status != 'READY'::task_status
            """;

        Integer count = jdbcClient.sql(sql)
            .param("docId", docId)
            .query(Integer.class)
            .single();

        return count == 0;
    }

    @Override
    public void updateStatus(UUID chunkId, DocumentTaskStatus status) {
        String sql = """
            UPDATE document_chunks 
            SET status = :status::task_status, updated_at = NOW()
            WHERE id = :id
            """;

        int rowsAffected = jdbcClient.sql(sql)
            .param("status", status.name())
            .param("id", chunkId)
            .update();

        if (rowsAffected == 0) {
            throw new EntityNotFoundException(chunkId);
        }
    }

    @Override
    public void markAsFailed(UUID chunkId, String error) {
        String sql = """
            UPDATE document_chunks 
            SET status = :status::task_status,
                error_message = :error,
                updated_at = NOW()
            WHERE id = :id
            """;

        int rowsAffected = jdbcClient.sql(sql)
            .param("status", DocumentTaskStatus.FAILED.name())
            .param("error", error)
            .param("id", chunkId)
            .update();

        if (rowsAffected == 0) {
            throw new EntityNotFoundException(chunkId);
        }
    }

    @Override
    public void insertChunkVector(UUID docId, UUID chunkId, String content, float[] vector) {
        String sql = """
            INSERT INTO 
            document_chunk_embeddings 
            (document_id, chunk_id, content, embedding) 
            VALUES (?, ?, ?, ?)
            """;

        jdbcTemplate.update(sql,
            docId,
            chunkId,
            content,
            new PGvector(vector)
        );
    }

    @Override
    public List<DocumentSearchResultItem> findSimilar(float[] vector, int limit, Optional<UUID> clientId) {
        PGvector pgVector = new PGvector(vector);

        // We use 1 - (dist) to convert distance to a similarity score (0.0 to 1.0)
        String sql = """
                 SELECT
                     ce.content,
                     d.client_id,
                     1 - (ce.embedding <=> :vector) as score,
                     d.title,
                     d.summary,
                     d.status,
                     d.id as doc_id,
                     d.created_at
                 FROM document_chunk_embeddings ce
                 JOIN documents d ON ce.document_id = d.id
                 WHERE
                 """ +
                     clientId.map(x -> "d.client_id = :clientId AND ").orElse("") +
                     """
                       1 - (ce.embedding <=> :vector) > :threshold
                     ORDER BY ce.embedding <=> :vector ASC
                     LIMIT :limit
                     """;

        var client = jdbcClient.sql(sql)
            .param("vector", pgVector)
            .param("limit", limit)
            .param("threshold", documentSimilarityThreshold);

        clientId.ifPresent(uuid -> client.param("clientId", uuid));

        return client.query((rs, rowNum) -> new DocumentSearchResultItem(
            rs.getObject("doc_id", UUID.class),
            rs.getObject("client_id", UUID.class),
            rs.getString("title"),
            rs.getDouble("score"),
            rs.getString("summary"),
            DocumentTaskStatus.valueOf(rs.getString("status")),
            rs.getObject("created_at", OffsetDateTime.class)
        )).list();
    }

    @Override
    public List<UUID> resetStaleAndFailedChunks() {
        String sql = """
            UPDATE document_chunks 
            SET status = 'PENDING'::task_status, 
                attempts = attempts + 1, 
                updated_at = NOW()
            WHERE (
                status = 'FAILED'::task_status 
                OR (status = 'PROCESSING'::task_status AND updated_at < NOW() - (INTERVAL '1 minute' * :staleMins))
            )
            AND attempts < :maxAttempts   
            RETURNING document_id        
            """;

        return jdbcClient.sql(sql)
            .param("maxAttempts", maxAttempts)
            .param("staleMins", staleThresholdMinutes)
            .query(UUID.class)
            .list();
    }
}