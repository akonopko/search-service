package com.nevis.search.repository;

import com.nevis.search.model.Client;
import com.nevis.search.model.Document;
import com.nevis.search.model.DocumentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcDocumentRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Test
    @DisplayName("Should successfully save and then find a document by ID")
    void shouldSaveAndFindDocument() {
        // 1. GIVEN: Создаем клиента, так как документ требует clientId
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
            owner.id(), // Привязываем к созданному клиенту
            "TDD Principles",
            "Content about Red-Green-Refactor cycle...",
            "Short summary of TDD",
            DocumentStatus.PENDING,
            null,
            null
        );

        // 2. WHEN: Сохраняем документ
        Document savedDoc = documentRepository.save(newDoc);

        // 3. THEN: Проверяем, что всё сохранилось корректно
        assertThat(savedDoc.id()).isNotNull();
        assertThat(savedDoc.title()).isEqualTo("TDD Principles");
        assertThat(savedDoc.clientId()).isEqualTo(owner.id());
        assertThat(savedDoc.status()).isEqualTo(DocumentStatus.PENDING);
        assertThat(savedDoc.createdAt()).isNotNull();

        // Проверка через findById
        var foundDocOptional = documentRepository.findById(savedDoc.id());
        assertThat(foundDocOptional).isPresent();
        
        Document foundDoc = foundDocOptional.get();
        assertThat(foundDoc.content()).isEqualTo("Content about Red-Green-Refactor cycle...");
    }

    @Test
    @DisplayName("Should return empty Optional when finding non-existent document")
    void shouldReturnEmptyWhenNotFound() {
        var found = documentRepository.findById(UUID.randomUUID());
        assertThat(found).isEmpty();
    }
}