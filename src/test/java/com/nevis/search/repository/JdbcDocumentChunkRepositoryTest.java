package com.nevis.search.repository;

import com.nevis.search.exception.ChunkNotFoundException;
import com.nevis.search.exception.DocumentNotFoundException;
import com.nevis.search.model.Client;
import com.nevis.search.model.Document;
import com.nevis.search.model.DocumentChunk;
import com.nevis.search.model.DocumentTaskStatus;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JdbcDocumentChunkRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private DocumentChunkRepository chunkRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("Constraint: Fail when client_id does not exist (FK)")
    void shouldFailWithNonExistentClientId() {
        Document orphan = new Document(null, UUID.randomUUID(), "Title", "Content", null, DocumentTaskStatus.PENDING, null, null);
        assertThrows(DataIntegrityViolationException.class, () -> documentRepository.save(orphan));
    }

    @Test
    @DisplayName("Trigger: updated_at should change on update")
    void shouldUpdateTimestampOnRowChange() throws InterruptedException {
        Client owner = clientRepository.save(new Client(null, "Time", "Test", "time@test.com", null, List.of(), null, null));
        Document doc = documentRepository.save(new Document(null, owner.id(), "Initial", "Content", null, DocumentTaskStatus.PENDING, null, null));
        OffsetDateTime created = doc.updatedAt();

        Thread.sleep(100);
        jdbcClient.sql("UPDATE documents SET title = 'New' WHERE id = ?").param(doc.id()).update();

        Document updated = documentRepository.findById(doc.id()).orElseThrow();
        assertThat(updated.updatedAt()).isAfter(created);
    }

    @Test
    @DisplayName("Default: Use default PENDING status if not provided")
    void shouldApplyDefaultStatus() {
        Client owner = clientRepository.save(new Client(null, "Def", "Status", "def@test2.com", null, List.of(), null, null));
        Document saved = jdbcClient.sql("INSERT INTO documents (client_id, title, content) VALUES (?, ?, ?) RETURNING *")
            .param(owner.id()).param("Title").param("Content")
            .query((rs, rowNum) -> new Document(
                rs.getObject("id", UUID.class), owner.id(), "Title", "Content", null,
                DocumentTaskStatus.valueOf(rs.getString("status")), null, null))
            .single();

        assertThat(saved.status()).isEqualTo(DocumentTaskStatus.PENDING);
    }


    @Test
    @DisplayName("Chunks: insert in batch")
    void shouldInsertChunksInBatch() {

        Client owner = clientRepository.save(new Client(null, "Time", "Test", "time@test2.com", null, List.of(), null, null));
        Document doc = documentRepository.save(new Document(null, owner.id(), "Initial", "Content", null, DocumentTaskStatus.PENDING, null, null));

        List<TextSegment> segments = List.of(
            TextSegment.from("Chunk content one"),
            TextSegment.from("Chunk content two"),
            TextSegment.from("Chunk content three")
        );

        chunkRepository.saveChunks(doc.id(), segments);

        List<String> contents = jdbcClient.sql("SELECT content FROM document_chunks WHERE document_id = ?")
            .params(doc.id())
            .query(String.class)
            .list();

        assertThat(contents)
            .hasSize(3)
            .containsExactlyInAnyOrder(
                "Chunk content one",
                "Chunk content two",
                "Chunk content three"
            );
    }

    @Test
    @DisplayName("Chunks: handle empty")
    void shouldHandleEmptySegmentsList() {
        UUID docId = UUID.randomUUID();

        assertThatCode(() -> chunkRepository.saveChunks(docId, List.of()))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Chunks: fail on orphan")
    void shouldThrowExceptionWhenDocumentIdDoesNotExist() {
        UUID nonExistentId = UUID.randomUUID();
        List<TextSegment> segments = List.of(TextSegment.from("Orphaned chunk"));

        assertThatThrownBy(() -> chunkRepository.saveChunks(nonExistentId, segments))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Nested
    @DisplayName("Embeddings test")
    class EmbeddingsTest {

        @Test
        @DisplayName("Should retrieve only pending chunks for specific document in chronological order")
        void shouldFindOnlyPendingChunksForCorrectDoc() {

            Client client = clientRepository.save(new Client(null, "Def", "Status", "def@test.com", null, List.of(), null, null));

            UUID doc1Id = UUID.randomUUID();
            UUID doc2Id = UUID.randomUUID();
            insertTestDocument(doc1Id, client.id());
            insertTestDocument(doc2Id, client.id());

            // Chunks for Doc 1 (The one we will query)
            insertChunk(doc1Id, "First Pending", "PENDING", 1);
            insertChunk(doc1Id, "Second Pending", "PENDING", 2);
            insertChunk(doc1Id, "Already Ready", "READY", 3);

            // Chunk for Doc 2 (Should be ignored)
            insertChunk(doc2Id, "Other Doc Pending", "PENDING", 4);

            List<DocumentChunk> results = chunkRepository.startProcessing(doc1Id);

            assertThat(results)
                .hasSize(2)
                .extracting(DocumentChunk::content)
                .containsExactly("First Pending", "Second Pending");

            assertThat(results).allMatch(chunk -> chunk.status() == DocumentTaskStatus.PROCESSING);
        }

        private void insertTestDocument(UUID id, UUID clientId) {
            jdbcClient.sql("INSERT INTO documents (id, client_id, title, content, status) VALUES (?, ?, 'Title', 'Content', 'PENDING')")
                .params(id, clientId).update();
        }

        private UUID insertChunk(UUID docId, String content, String status, int delaySeconds) {
            return jdbcClient.sql("""
                                      INSERT INTO document_chunks (document_id, content, status, created_at) 
                                      VALUES (?, ?, ?::task_status, CURRENT_TIMESTAMP + INTERVAL '""" + delaySeconds + """
                                      ' SECOND)
                                      RETURNING id
                                      """)
                .params(docId, content, status)
                .query(UUID.class)
                .single();
        }


        @Test
        @DisplayName("Should successfully update chunk status")
        void shouldUpdateEmbeddingStatus() {

            Client client = clientRepository.save(new Client(null, "Name1", "Last1", "def@test3.com", null, List.of(), null, null));

            UUID doc1Id = UUID.randomUUID();
            UUID doc2Id = UUID.randomUUID();
            insertTestDocument(doc1Id, client.id());
            insertTestDocument(doc2Id, client.id());

            UUID existingChunkId = insertChunk(doc1Id, "First Pending", "PENDING", 1);

            chunkRepository.updateStatus(existingChunkId, DocumentTaskStatus.PROCESSING);

            String updatedStatus = jdbcClient.sql("SELECT status FROM document_chunks WHERE id = ?")
                .param(existingChunkId)
                .query(String.class)
                .single();

            assertThat(updatedStatus).isEqualTo("PROCESSING");
        }

        @Test
        @DisplayName("Should throw exception when updating status of non-existent chunk")
        void shouldThrowExceptionWhenChunkNotFoundForStatus() {
            UUID randomId = UUID.randomUUID();
            assertThatThrownBy(() -> chunkRepository.updateStatus(randomId, DocumentTaskStatus.READY))
                .isInstanceOf(ChunkNotFoundException.class)
                .hasMessageContaining("Chunk not found: " + randomId);
        }

        @Test
        @DisplayName("Should update vector and set status to READY")
        void shouldUpdateEmbeddingChunkVector() {
            Client client = clientRepository.save(new Client(null, "Name2", "Last2", "def@test4.com", null, List.of(), null, null));

            UUID doc1Id = UUID.randomUUID();
            UUID doc2Id = UUID.randomUUID();
            insertTestDocument(doc1Id, client.id());
            insertTestDocument(doc2Id, client.id());

            UUID existingChunkId = insertChunk(doc1Id, "First Pending", "PENDING", 1);

            float[] vector = new float[1536];
            vector[0] = 0.1f;
            vector[1535] = 0.9f;

            chunkRepository.updateVector(existingChunkId, vector);

            Map<String, Object> result = jdbcClient.sql("SELECT status, embedding FROM document_chunks WHERE id = ?")
                .param(existingChunkId)
                .query()
                .singleRow();

            assertThat(result.get("status")).isEqualTo("READY");
            assertThat(result.get("embedding")).isNotNull();
        }

        @Test
        @DisplayName("Should throw exception when updating vector of non-existent chunk")
        void shouldThrowExceptionWhenChunkNotFoundForVector() {
            UUID randomId = UUID.randomUUID();

            float[] vector = new float[1536];
            vector[0] = 0.1f;
            vector[1] = 0.2f;

            assertThatThrownBy(() -> chunkRepository.updateVector(randomId, vector))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Chunk not found: " + randomId);
        }
    }

    @Nested
    @DisplayName("Update document status")
    class DocumentStatusTest {

        private UUID documentId;
        private UUID chunkId;

        @BeforeEach
        void setUp() {
            jdbcClient.sql("delete from clients").update();
            jdbcClient.sql("delete from documents").update();
            jdbcClient.sql("delete from document_chunks").update();

            UUID clientId = UUID.randomUUID();
            jdbcClient.sql("INSERT INTO clients (id, first_name, last_name, email) VALUES (?, 'Test', 'User', 'test@ai.com')")
                .params(clientId).update();

            documentId = UUID.randomUUID();
            jdbcClient.sql("""
                    INSERT INTO documents (id, client_id, title, content, status) 
                    VALUES (?, ?, 'Test Doc', 'Source content', 'PENDING'::task_status)
                """).params(documentId, clientId).update();

            chunkId = UUID.randomUUID();
            jdbcClient.sql("""
                    INSERT INTO document_chunks (id, document_id, content, status) 
                    VALUES (?, ?, 'Chunk content', 'PENDING'::task_status)
                """).params(chunkId, documentId).update();
        }

        @Test
        @DisplayName("Should update document status successfully")
        void shouldUpdateDocumentStatus() {
            documentRepository.updateStatus(documentId, DocumentTaskStatus.READY);

            // Assert
            String currentStatus = jdbcClient.sql("SELECT status::text FROM documents WHERE id = ?")
                .param(documentId)
                .query(String.class).single();

            assertThat(currentStatus).isEqualTo("READY");
        }

        @Test
        @DisplayName("Should throw DocumentNotFoundException for invalid ID")
        void shouldThrowWhenDocumentNotFound() {
            UUID randomId = UUID.randomUUID();
            assertThatThrownBy(() -> documentRepository.updateStatus(randomId, DocumentTaskStatus.PROCESSING))
                .isInstanceOf(DocumentNotFoundException.class);
        }

        @Test
        @DisplayName("Should update embedding status and error message")
        void shouldUpdateEmbeddingStatusWithError() {
            String errorMessage = "API Quota exceeded";

            chunkRepository.markAllDocumentChunksAsFailed(documentId, errorMessage);

            Map<String, Object> result = jdbcClient.sql("SELECT status::text, error_message FROM document_chunks WHERE id = ?")
                .param(chunkId)
                .query().singleRow();

            assertThat(result.get("status")).isEqualTo("FAILED");
            assertThat(result.get("error_message")).isEqualTo(errorMessage);
        }

        @Test
        @DisplayName("Should update embedding status with null error")
        void shouldUpdateEmbeddingStatusWithoutError() {
            chunkRepository.updateStatus(chunkId, DocumentTaskStatus.READY);

            Map<String, Object> result = jdbcClient.sql("SELECT status::text, error_message FROM document_chunks WHERE id = ?")
                .param(chunkId)
                .query().singleRow();

            assertThat(result.get("status")).isEqualTo("READY");
            assertThat(result.get("error_message")).isNull();
        }

        @Test
        @DisplayName("Should throw ChunkNotFoundException for invalid ID")
        void shouldThrowWhenChunkNotFound() {
            UUID randomId = UUID.randomUUID();
            assertThatThrownBy(() -> chunkRepository.updateStatus(randomId, DocumentTaskStatus.READY))
                .isInstanceOf(ChunkNotFoundException.class);
        }

    }


    @Nested
    @DisplayName("Update document status")
    class AllChunksProcessedTest {

        private UUID docId;

        @BeforeEach
        void setUp() {
            jdbcClient.sql("delete from clients").update();
            jdbcClient.sql("delete from documents").update();
            jdbcClient.sql("delete from document_chunks").update();

            UUID clientId = UUID.randomUUID();
            jdbcClient.sql("INSERT INTO clients (id, first_name, last_name, email) VALUES (?, 'Test', 'User', 'test@test.com')")
                .params(clientId).update();

            docId = UUID.randomUUID();
            jdbcClient.sql("INSERT INTO documents (id, client_id, title, content, status) VALUES (?, ?, 'Doc', 'Content', 'PROCESSING'::task_status)")
                .params(docId, clientId).update();
        }

        @Test
        @DisplayName("Should return true if all chunks are in READY status")
        void shouldReturnTrueWhenAllChunksReady() {
            insertChunk(docId, DocumentTaskStatus.READY);
            insertChunk(docId, DocumentTaskStatus.READY);
            boolean result = chunkRepository.areAllChunksProcessed(docId);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false if at least one chunk is PENDING")
        void shouldReturnFalseWhenChunksArePending() {
            insertChunk(docId, DocumentTaskStatus.READY);
            insertChunk(docId, DocumentTaskStatus.PENDING);
            boolean result = chunkRepository.areAllChunksProcessed(docId);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return false if a chunk has FAILED")
        void shouldReturnFalseWhenChunksHaveFailed() {
            insertChunk(docId, DocumentTaskStatus.FAILED);
            boolean result = chunkRepository.areAllChunksProcessed(docId);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return true if no chunks exist (edge case)")
        void shouldReturnTrueIfNoChunksExist() {
            boolean result = chunkRepository.areAllChunksProcessed(docId);
            assertThat(result).isTrue();
        }

        private void insertChunk(UUID documentId, DocumentTaskStatus status) {
            jdbcClient.sql("INSERT INTO document_chunks (id, document_id, content, status) VALUES (?, ?, 'Content', ?::task_status)")
                .params(UUID.randomUUID(), documentId, status.name())
                .update();
        }

    }


}