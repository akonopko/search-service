package com.nevis.search.service;

import com.nevis.search.controller.ClientSearchResponse;
import com.nevis.search.controller.DocumentSearchResponse;
import com.nevis.search.controller.DocumentSearchResultItem;
import com.nevis.search.exception.EntityNotFoundException;
import com.nevis.search.exception.WrongQueryException;
import com.nevis.search.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final ClientService clientService;
    private final DocumentService documentService;
    private final EmbeddingService embeddingService;

    private static final int MIN_QUERY_LENGTH = 3;
    private static final int MAX_QUERY_LENGTH = 500;
    private static final double MIN_SIMILARITY_THRESHOLD = 0.25;

    @Value("${app.search.account.limit:}")
    private Integer accountSearchLimit;

    @Value("${app.search.account.similarity:0.55}")
    private Double accountSearchThreshold;

    @Value("${app.search.document.limit:}")
    private Integer documentSearchLimit;

    @Override
    public ClientSearchResponse findClient(String query) {
        validateQuery(query);
        log.debug("Searching for clients with query: {}", query);
        return clientService.search(query, Optional.ofNullable(accountSearchLimit), Optional.ofNullable(accountSearchThreshold));
    }

    @Override
    public DocumentSearchResponse findDocument(Optional<UUID> clientId, String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query cannot be blank");
        }

        clientId.ifPresent(clientService::getById);

        log.debug("Performing semantic search for documents. ClientId: {}, Query: {}", clientId, query);

        try {
            float[] queryVector = embeddingService.embedQuery(query);

            List<DocumentSearchResultItem> rawResults = documentService.search(
                queryVector, Optional.ofNullable(documentSearchLimit), clientId
            );

            List<DocumentSearchResultItem> filteredResults = rawResults.stream()
                .filter(doc -> doc.score() >= MIN_SIMILARITY_THRESHOLD)
                .toList();

            return new DocumentSearchResponse(filteredResults);
        } catch (Exception e) {
            log.error("Semantic search failed for query: {}", query, e);
            return new DocumentSearchResponse(List.of());
        }
    }

    private void validateQuery(String query) {
        if (query == null || query.trim().length() < MIN_QUERY_LENGTH) {
            throw new WrongQueryException("Query too short");
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new WrongQueryException("Query too long");
        }
    }
}