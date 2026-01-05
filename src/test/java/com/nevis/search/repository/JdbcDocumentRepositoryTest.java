package com.nevis.search.repository;

import com.nevis.search.exception.EntityNotFoundException;
import com.nevis.search.model.Client;
import com.nevis.search.model.Document;
import com.nevis.search.model.DocumentTaskStatus;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JdbcDocumentRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("Happy path: Should successfully save and then find a document by ID")
    void shouldSaveAndFindDocument() {
        Client owner = clientRepository.save(new Client(
            null,
            "John",
            "Doe",
            "john.doe@example.com",
            "Owner of documents",
            List.of(),
            null,
            null
        ));

        Document newDoc = new Document(
            null,
            owner.id(),
            "Title",
            "Content",
            "Short summary of TDD",
            DocumentTaskStatus.PENDING,
            null,
            0,
            DocumentTaskStatus.PENDING,
            null,
            null
        );

        Document savedDoc = documentRepository.save(newDoc);

        assertThat(savedDoc.id()).isNotNull();
        assertThat(savedDoc.title()).isEqualTo("Title");
        assertThat(savedDoc.clientId()).isEqualTo(owner.id());
        assertThat(savedDoc.status()).isEqualTo(DocumentTaskStatus.PENDING);
        assertThat(savedDoc.summaryStatus()).isEqualTo(DocumentTaskStatus.PENDING);
        assertThat(savedDoc.createdAt()).isNotNull();

        var foundDocOptional = documentRepository.findById(savedDoc.id());
        assertThat(foundDocOptional).isPresent();

        Document foundDoc = foundDocOptional.get();
        assertThat(foundDoc.content()).isEqualTo("Content");
    }

    @Test
    @DisplayName("Should return empty Optional when finding non-existent document")
    void shouldReturnEmptyWhenNotFound() {
        var found = documentRepository.findById(UUID.randomUUID());
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Constraint: Fail when client_id does not exist (FK)")
    void shouldFailWithNonExistentClientId() {
        Document orphan = new Document(null, UUID.randomUUID(), "Title", "Content", null, DocumentTaskStatus.PENDING, null, 0, DocumentTaskStatus.PENDING, null, null);
        assertThrows(DataIntegrityViolationException.class, () -> documentRepository.save(orphan));
    }

    @Test
    @DisplayName("Trigger: updated_at should change on update")
    void shouldUpdateTimestampOnRowChange() throws InterruptedException {
        Client owner = clientRepository.save(new Client(null, "Time", "Test", "time@test.com", null, List.of(), null, null));
        Document doc = documentRepository.save(new Document(null, owner.id(), "Initial", "Content", null, DocumentTaskStatus.PENDING, null, 0, DocumentTaskStatus.PENDING, null, null));
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
                rs.getObject("id", UUID.class),
                owner.id(),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("summary"),
                DocumentTaskStatus.valueOf(rs.getString("summary_status")),
                rs.getString("summary_error_message"),
                rs.getInt("summary_attempts"),
                DocumentTaskStatus.valueOf(rs.getString("status")),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)))
            .single();

        assertThat(saved.status()).isEqualTo(DocumentTaskStatus.PENDING);
        assertThat(saved.summaryStatus()).isEqualTo(DocumentTaskStatus.PENDING);
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
                    INSERT INTO documents (id, client_id, title, content, status, summary_status, summary_attempts)
                    VALUES (?, ?, 'Test Doc', 'Source content', 'PENDING'::task_status, 'PENDING'::task_status, 0)
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
                .isInstanceOf(EntityNotFoundException.class);
        }

    }

    @Nested
    @DisplayName("Document Summary Updates")
    class DocumentSummaryTest {

        private UUID documentId;

        @BeforeEach
        void setUp() {
            jdbcClient.sql("DELETE FROM documents").update();
            jdbcClient.sql("DELETE FROM clients").update();

            UUID clientId = UUID.randomUUID();
            jdbcClient.sql("INSERT INTO clients (id, first_name, last_name, email) VALUES (?, 'Summary', 'Tester', 'sum@test.com')")
                .params(clientId).update();

            documentId = UUID.randomUUID();
            jdbcClient.sql("""
                    INSERT INTO documents (id, client_id, title, content, status, summary_status, summary_attempts)
                    VALUES (?, ?, 'Summary Doc', 'Long content for AI', 'PENDING'::task_status, 'PENDING'::task_status, 0)
                """).params(documentId, clientId).update();
        }

        @Test
        @DisplayName("Should update summary status and error message when generation fails")
        void shouldUpdateSummaryStatusOnError() {
            String errorMsg = "Safety filter triggered: sensitive content";

            documentRepository.updateSummaryStatus(documentId, DocumentTaskStatus.FAILED, errorMsg);

            var result = jdbcClient.sql("SELECT summary_status::text, summary_error_message FROM documents WHERE id = ?")
                .param(documentId)
                .query((rs, rowNum) -> List.of(rs.getString(1), rs.getString(2)))
                .single();

            assertThat(result.get(0)).isEqualTo("FAILED");
            assertThat(result.get(1)).isEqualTo(errorMsg);
        }

        @Test
        @DisplayName("Should save generated summary and set status to READY")
        void shouldSaveSummarySuccessfully() {
            String generatedSummary = "This document describes AI integration testing patterns.";

            documentRepository.updateSummary(documentId, generatedSummary, DocumentTaskStatus.READY);

            Document updatedDoc = documentRepository.findById(documentId).orElseThrow();

            assertThat(updatedDoc.summary()).isEqualTo(generatedSummary);
            assertThat(updatedDoc.summaryStatus()).isEqualTo(DocumentTaskStatus.READY);
        }

        @Test
        @DisplayName("Should clear error message when summary is successfully updated")
        void shouldClearErrorOnSuccess() {
            documentRepository.updateSummaryStatus(documentId, DocumentTaskStatus.FAILED, "Old Error");

            documentRepository.updateSummary(documentId, "New Summary", DocumentTaskStatus.READY);

            String errorInDb = jdbcClient.sql("SELECT summary_error_message FROM documents WHERE id = ?")
                .param(documentId)
                .query(String.class).optional().orElse(null);

            assertThat(errorInDb).isNull();
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException when updating non-existent document")
        void shouldThrowExceptionForMissingDoc() {
            UUID fakeId = UUID.randomUUID();

            assertThatThrownBy(() -> documentRepository.updateSummary(fakeId, "Sum", DocumentTaskStatus.READY))
                .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Worker: Reset Stale and Failed Summaries Tests")
    class ResetSummaryWorkerTests {

        private UUID clientId;

        @BeforeEach
        void setUp() {
            jdbcClient.sql("DELETE FROM documents").update();
            jdbcClient.sql("DELETE FROM clients").update();

            clientId = UUID.randomUUID();
            jdbcClient.sql("INSERT INTO clients (id, first_name, last_name, email) VALUES (?, 'Worker', 'Tester', 'worker@test.com')")
                .params(clientId).update();
        }

        @Test
        @DisplayName("Should reset FAILED status to PENDING if attempts are under limit")
        void shouldResetFailedSummaries() {
            UUID docId = UUID.randomUUID();
            jdbcClient.sql("""
                INSERT INTO documents (id, client_id, title, content, summary_status, summary_attempts, status)
                VALUES (?, ?, 'Failed Doc', 'Content', 'FAILED'::task_status, 1, 'PENDING'::task_status)
                """).params(docId, clientId).update();

            List<UUID> resetIds = documentRepository.resetStaleAndFailedSummaries(3, 10);

            assertThat(resetIds).containsExactly(docId);

            String currentStatus = jdbcClient.sql("SELECT summary_status::text FROM documents WHERE id = ?")
                .param(docId).query(String.class).single();
            assertThat(currentStatus).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("Should NOT reset FAILED status if attempts reached max limit")
        void shouldNotResetIfMaxAttemptsReached() {
            UUID docId = UUID.randomUUID();
            jdbcClient.sql("""
                INSERT INTO documents (id, client_id, title, content, summary_status, summary_attempts, status)
                VALUES (?, ?, 'Maxed Doc', 'Content', 'FAILED'::task_status, 5, 'PENDING'::task_status)
                """).params(docId, clientId).update();

            List<UUID> resetIds = documentRepository.resetStaleAndFailedSummaries(5, 10);

            assertThat(resetIds).isEmpty();
        }

        @Test
        @DisplayName("Should reset PROCESSING status if updated_at is older than stale threshold")
        void shouldResetStaleProcessingSummaries() {
            UUID docId = UUID.randomUUID();
            jdbcClient.sql("""
                INSERT INTO documents (id, client_id, title, content, summary_status, summary_attempts, updated_at, status)
                VALUES (?, ?, 'Stale Doc', 'Content', 'PROCESSING'::task_status, 0, NOW() - INTERVAL '20 minutes', 'PENDING'::task_status)
                """).params(docId, clientId).update();

            List<UUID> resetIds = documentRepository.resetStaleAndFailedSummaries(3, 10);

            assertThat(resetIds).containsExactly(docId);

            String currentStatus = jdbcClient.sql("SELECT summary_status::text FROM documents WHERE id = ?")
                .param(docId).query(String.class).single();
            assertThat(currentStatus).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("Should NOT reset PROCESSING status if doc is still within stale threshold")
        void shouldNotResetFreshProcessingSummaries() {
            UUID docId = UUID.randomUUID();
            jdbcClient.sql("""
                INSERT INTO documents (id, client_id, title, content, summary_status, summary_attempts, updated_at, status)
                VALUES (?, ?, 'Fresh Doc', 'Content', 'PROCESSING'::task_status, 0, NOW(), 'PENDING'::task_status)
                """).params(docId, clientId).update();

            List<UUID> resetIds = documentRepository.resetStaleAndFailedSummaries(3, 10);

            assertThat(resetIds).isEmpty();
        }
    }

    @Nested
    @DisplayName("Document Summary: claimForSummary Tests")
    class ClaimSummaryTests {

        private UUID clientId;
        private UUID docId;

        @BeforeEach
        void setUp() {
            jdbcClient.sql("DELETE FROM documents").update();
            jdbcClient.sql("DELETE FROM clients").update();

            clientId = UUID.randomUUID();
            jdbcClient.sql("INSERT INTO clients (id, first_name, last_name, email) VALUES (?, 'Claim', 'Tester', 'claim@test.com')")
                .params(clientId).update();

            docId = UUID.randomUUID();
        }

        @Test
        @DisplayName("Should successfully claim a PENDING document and increment attempts")
        void shouldClaimPendingDocument() {
            jdbcClient.sql("""
                INSERT INTO documents (id, client_id, title, content, summary_status, summary_attempts, status)
                VALUES (?, ?, 'Claimable', 'Content', 'PENDING'::task_status, 0, 'PENDING'::task_status)
                """).params(docId, clientId).update();

            Optional<Document> claimed = documentRepository.claimForSummary(docId, 3);

            assertThat(claimed).isPresent();
            assertThat(claimed.get().summaryStatus()).isEqualTo(DocumentTaskStatus.PROCESSING);
            assertThat(claimed.get().summaryAttempts()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should NOT claim if document status is already READY")
        void shouldNotClaimReadyDocument() {
            jdbcClient.sql("""
                INSERT INTO documents (id, client_id, title, content, summary_status, summary_attempts, status)
                VALUES (?, ?, 'Ready Doc', 'Content', 'READY'::task_status, 0, 'PENDING'::task_status)
                """).params(docId, clientId).update();

            Optional<Document> claimed = documentRepository.claimForSummary(docId, 3);

            assertThat(claimed).isEmpty();
        }

        @Test
        @DisplayName("Should NOT claim if summary_attempts has reached maxAttempts")
        void shouldNotClaimIfMaxAttemptsReached() {
            jdbcClient.sql("""
                INSERT INTO documents (id, client_id, title, content, summary_status, summary_attempts, status)
                VALUES (?, ?, 'Failed Doc', 'Content', 'PENDING'::task_status, 3, 'PENDING'::task_status)
                """).params(docId, clientId).update();

            Optional<Document> claimed = documentRepository.claimForSummary(docId, 3);

            assertThat(claimed).isEmpty();
        }

        @Test
        @DisplayName("Concurrency: Only one thread should be able to claim a document")
        void concurrencyTest() throws Exception {
            jdbcClient.sql("""
                INSERT INTO documents (id, client_id, title, content, summary_status, summary_attempts, status)
                VALUES (?, ?, 'Concurrent Doc', 'Content', 'PENDING'::task_status, 0, 'PENDING'::task_status)
                """).params(docId, clientId).update();

            var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
            java.util.concurrent.Callable<Optional<Document>> task = () -> documentRepository.claimForSummary(docId, 3);

            var results = executor.invokeAll(List.of(task, task));

            int successCount = 0;
            for (var res : results) {
                if (res.get().isPresent()) successCount++;
            }

            assertThat(successCount).isEqualTo(1);

            executor.shutdown();
        }
    }
}