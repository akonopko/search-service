package com.nevis.search.repository;

import com.nevis.search.exception.ChunkNotFoundException;
import com.nevis.search.exception.DocumentNotFoundException;
import com.nevis.search.model.Document;
import com.nevis.search.model.DocumentChunk;
import com.nevis.search.model.DocumentTaskStatus;
import com.nevis.search.service.DocumentSearchResult;
import com.pgvector.PGvector;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcDocumentChunkRepository implements DocumentChunkRepository {

    private final JdbcClient jdbcClient;

    private final JdbcTemplate jdbcTemplate;

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
    public List<DocumentChunk> startProcessing(UUID docId) {
        String sql = """
            UPDATE document_chunks
            SET status = 'PROCESSING'::task_status
            WHERE document_id = :docId 
              AND status = 'PENDING'::task_status
            RETURNING id, document_id, content, chunk_summary, 
                      status, error_message, 
                      attempts, created_at, updated_at
            """;

        return jdbcClient.sql(sql)
            .param("docId", docId)
            .query(documentChunkMapper)
            .list();
    }

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
            SET status = :status::task_status
            WHERE id = :id
            """;

        int rowsAffected = jdbcClient.sql(sql)
            .param("status", status.name())
            .param("id", chunkId)
            .update();

        if (rowsAffected == 0) {
            throw new ChunkNotFoundException(chunkId);
        }
    }

    @Override
    public void markAllDocumentChunksAsFailed(UUID documentId, String error) {
        String sql = """
            UPDATE document_chunks 
            SET status = :status::task_status,
                error_message = :error
            WHERE document_id = :id
            """;

        int rowsAffected = jdbcClient.sql(sql)
            .param("status", DocumentTaskStatus.FAILED.name())
            .param("error", error)
            .param("id", documentId)
            .update();

        if (rowsAffected == 0) {
            throw new DocumentNotFoundException(documentId);
        }
    }

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

}