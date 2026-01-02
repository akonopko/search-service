package com.nevis.search.repository;

import com.nevis.search.model.Client;
import com.nevis.search.model.Document;
import com.nevis.search.model.DocumentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcDocumentRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("Should successfully save and then find a document by ID")
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
            DocumentStatus.PENDING,
            null,
            null
        );

        Document savedDoc = documentRepository.save(newDoc);

        assertThat(savedDoc.id()).isNotNull();
        assertThat(savedDoc.title()).isEqualTo("Title");
        assertThat(savedDoc.clientId()).isEqualTo(owner.id());
        assertThat(savedDoc.status()).isEqualTo(DocumentStatus.PENDING);
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
    @DisplayName("Happy Path: Save document for client")
    void shouldSaveDocument() {
        Client owner = clientRepository.save(new Client(null, "Ivan", "Ivanov", "ivan@test.com", null, List.of(), null, null));
        Document doc = new Document(null, owner.id(), "Doc Title", "Some content", "Summary", DocumentStatus.PENDING, null, null);

        Document saved = documentRepository.save(doc);

        assertThat(saved.id()).isNotNull();
        assertThat(saved.clientId()).isEqualTo(owner.id());
        assertThat(saved.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("Constraint: Fail when client_id does not exist (FK)")
    void shouldFailWithNonExistentClientId() {
        Document orphan = new Document(null, UUID.randomUUID(), "Title", "Content", null, DocumentStatus.PENDING, null, null);
        assertThrows(DataIntegrityViolationException.class, () -> documentRepository.save(orphan));
    }

    @Test
    @DisplayName("Trigger: updated_at should change on update")
    void shouldUpdateTimestampOnRowChange() throws InterruptedException {
        Client owner = clientRepository.save(new Client(null, "Time", "Test", "time@test.com", null, List.of(), null, null));
        Document doc = documentRepository.save(new Document(null, owner.id(), "Initial", "Content", null, DocumentStatus.PENDING, null, null));
        OffsetDateTime created = doc.updatedAt();

        Thread.sleep(100);
        jdbcClient.sql("UPDATE documents SET title = 'New' WHERE id = ?").param(doc.id()).update();

        Document updated = documentRepository.findById(doc.id()).orElseThrow();
        assertThat(updated.updatedAt()).isAfter(created);
    }

    @Test
    @DisplayName("Default: Use default PENDING status if not provided")
    void shouldApplyDefaultStatus() {
        Client owner = clientRepository.save(new Client(null, "Def", "Status", "def@test.com", null, List.of(), null, null));
        Document saved = jdbcClient.sql("INSERT INTO documents (client_id, title, content) VALUES (?, ?, ?) RETURNING *")
            .param(owner.id()).param("Title").param("Content")
            .query((rs, rowNum) -> new Document(
                rs.getObject("id", UUID.class), owner.id(), "Title", "Content", null,
                DocumentStatus.valueOf(rs.getString("status")), null, null))
            .single();

        assertThat(saved.status()).isEqualTo(DocumentStatus.PENDING);
    }
}