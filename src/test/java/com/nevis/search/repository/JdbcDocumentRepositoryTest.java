package com.nevis.search.repository;

import com.nevis.search.exception.ChunkNotFoundException;
import com.nevis.search.exception.DocumentNotFoundException;
import com.nevis.search.model.Client;
import com.nevis.search.model.Document;
import com.nevis.search.model.DocumentChunk;
import com.nevis.search.model.DocumentTaskStatus;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.*;
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
            null
        );

        Document savedDoc = documentRepository.save(newDoc);

        assertThat(savedDoc.id()).isNotNull();
        assertThat(savedDoc.title()).isEqualTo("Title");
        assertThat(savedDoc.clientId()).isEqualTo(owner.id());
        assertThat(savedDoc.status()).isEqualTo(DocumentTaskStatus.PENDING);
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

    }


}