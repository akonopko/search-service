package com.nevis.search.repository;

import com.nevis.search.exception.ChunkNotFoundException;
import com.nevis.search.exception.DocumentNotFoundException;
import com.nevis.search.model.Client;
import com.nevis.search.model.Document;
import com.nevis.search.model.DocumentChunk;
import com.nevis.search.model.DocumentTaskStatus;
import com.nevis.search.service.DocumentSearchResult;
import dev.langchain4j.data.segment.TextSegment;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

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
        @DisplayName("Should insert chunk vector")
        void shouldUpdateEmbeddingChunkVector() {
            Client client = clientRepository.save(new Client(null, "Name2", "Last2", "def@test4.com", null, List.of(), null, null));
            UUID docId = UUID.randomUUID();
            insertTestDocument(docId, client.id());

            String content = "Test content for embedding";
            UUID existingChunkId = insertChunk(docId, content, "PENDING", 0);

            float[] vector = new float[768];
            vector[0] = 0.1f;
            vector[767] = 0.9f;

            chunkRepository.insertChunkVector(docId, existingChunkId, content, vector);

            Map<String, Object> embeddingResult = jdbcClient.sql("""
            SELECT embedding 
            FROM document_chunk_embeddings 
            WHERE chunk_id = ?
            """)
                .param(existingChunkId)
                .query()
                .singleRow();

            assertThat(embeddingResult.get("embedding")).isNotNull();
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

            chunkRepository.markAsFailed(chunkId, errorMessage);

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
            transactionTemplate = new TransactionTemplate(transactionManager);

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

        @Test
        @DisplayName("Should claim only one pending chunk and change its status")
        void shouldClaimOneChunk() {
            insertChunk(docId, DocumentTaskStatus.PENDING);
            insertChunk(docId, DocumentTaskStatus.PENDING);

            Optional<DocumentChunk> claimed = chunkRepository.claimNextPendingChunk(docId);

            assertThat(claimed).isPresent();
            assertThat(claimed.get().status()).isEqualTo(DocumentTaskStatus.PROCESSING);

            int remaining = jdbcClient.sql("SELECT count(*) FROM document_chunks WHERE status = 'PENDING'")
                .query(Integer.class).single();
            assertThat(remaining).isEqualTo(1);
        }

        @Test
        @DisplayName("Should count pending chunks correctly")
        void shouldCountPendingChunks() {
            insertChunk(docId, DocumentTaskStatus.PENDING);
            insertChunk(docId, DocumentTaskStatus.PENDING);
            insertChunk(docId, DocumentTaskStatus.READY);

            int count = chunkRepository.countPendingByDocumentId(docId);

            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("Should skip locked chunks when concurrent workers are active")
        void shouldSkipLockedChunks() throws Exception {
            UUID chunk1 = UUID.randomUUID();
            UUID chunk2 = UUID.randomUUID();
            insertChunkWithId(chunk1, docId, DocumentTaskStatus.PENDING);
            insertChunkWithId(chunk2, docId, DocumentTaskStatus.PENDING);

            CountDownLatch latch = new CountDownLatch(1);

            CompletableFuture<Optional<DocumentChunk>> thread1Claim = CompletableFuture.supplyAsync(() ->
                transactionTemplate.execute(status -> {
                    Optional<DocumentChunk> claim = chunkRepository.claimNextPendingChunk(docId);
                    latch.countDown();
                    try { Thread.sleep(1000); } catch (InterruptedException e) {}
                    return claim;
                })
            );

            latch.await();
            Optional<DocumentChunk> thread2Claim = chunkRepository.claimNextPendingChunk(docId);

            assertThat(thread1Claim.get()).isPresent();
            assertThat(thread2Claim).isPresent();
            assertThat(thread1Claim.get().get().id()).isNotEqualTo(thread2Claim.get().id());
        }

        private void insertChunkWithId(UUID id, UUID documentId, DocumentTaskStatus status) {
            jdbcClient.sql("INSERT INTO document_chunks (id, document_id, content, status) VALUES (?, ?, 'Content', ?::task_status)")
                .params(id, documentId, status.name())
                .update();
        }

    }


    @Nested
    @DisplayName("Search document chunk by vector")
    class DocumentChunkVectorSearch {
        @Test
        @DisplayName("Should find similar chunk using vectors from resource files (E2E Vector Match)")
        void shouldFindSimilarChunkUsingRealVectors() {
            float[] docVector = loadVector("vectors/proof_of_address/vector");
            float[] addressProof = loadVector("vectors/address_proof_query/vector");
            float[] utiliseBilly = loadVector("vectors/utilize_billy/vector");
            float[] utilityBill = loadVector("vectors/utility_bill/vector");
            float[] aSymbol = loadVector("vectors/a_symbol/vector");
            float[] gibberish = loadVector("vectors/gibberish/vector");

            UUID clientId = UUID.randomUUID();
            insertTestClient(clientId);

            UUID docId = UUID.randomUUID();
            insertTestDocument(docId, clientId, "Utility Bill - Jan 2026");

            String chunkContent = "Address: 742 Evergreen Terrace, Springfield. Utility usage details...";
            UUID chunkId = insertTestChunk(docId, chunkContent);

            insertTestEmbedding(docId, chunkId, chunkContent, docVector);

            assertQueryScore(docVector, chunkContent, 1.0, 1.0);
            assertQueryScore(addressProof, chunkContent, 0.8, 1.0);
            assertQueryScore(utiliseBilly, chunkContent, 0.5, 0.6);
            assertQueryScore(aSymbol, chunkContent, 0.5, 0.6);
            assertQueryScore(utilityBill, chunkContent, 0.5, 0.7);
            assertNoResult(gibberish);
        }

        private void assertNoResult(float[] queryVector) {
            List<DocumentSearchResult> results = chunkRepository.findSimilar(queryVector, 1, Optional.empty());

            assertThat(results).isEmpty();
        }

        private void assertQueryScore(float[] queryVector, String chunkContent, double scoreLower, double scoreUpper) {
            List<DocumentSearchResult> results = chunkRepository.findSimilar(queryVector, 1, Optional.empty());

            assertThat(results).isNotEmpty();
            DocumentSearchResult topResult = results.get(0);

            assertThat(topResult.title()).isEqualTo("Utility Bill - Jan 2026");
            assertThat(topResult.content()).isEqualTo(chunkContent);

            assertThat(topResult.score()).isGreaterThanOrEqualTo(scoreLower);
            assertThat(topResult.score()).isLessThanOrEqualTo(scoreUpper);
        }

        private void insertTestClient(UUID id) {
            jdbcTemplate.update(
                "INSERT INTO clients (id, first_name, last_name, email) VALUES (?, 'Homer', 'Simpson', ?)",
                id, id + "@springfield.com"
            );
        }

        private void insertTestDocument(UUID id, UUID clientId, String title) {
            jdbcTemplate.update(
                "INSERT INTO documents (id, client_id, title, content, status) VALUES (?, ?, ?, 'Full text', 'READY')",
                id, clientId, title
            );
        }

        private UUID insertTestChunk(UUID docId, String content) {
            UUID chunkId = UUID.randomUUID();
            jdbcTemplate.update(
                "INSERT INTO document_chunks (id, document_id, content, status) VALUES (?, ?, ?, 'READY')",
                chunkId, docId, content
            );
            return chunkId;
        }

        private void insertTestEmbedding(UUID docId, UUID chunkId, String content, float[] vector) {
            jdbcTemplate.update(
                "INSERT INTO document_chunk_embeddings (document_id, chunk_id, content, embedding) VALUES (?, ?, ?, ?::vector)",
                docId, chunkId, content, java.util.Arrays.toString(vector)
            );
        }

        @SneakyThrows
        private float[] loadVector(String path) {
            try (var inputStream = new ClassPathResource(path).getInputStream()) {
                String content = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
                String clean = content.replace("[", "").replace("]", "").trim();
                String[] parts = clean.split(",\\s*");

                float[] vector = new float[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    vector[i] = Float.parseFloat(parts[i]);
                }
                return vector;
            }
        }
    }


}