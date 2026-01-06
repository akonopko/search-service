package com.nevis.search;

import com.nevis.search.controller.*;
import com.nevis.search.model.DocumentTaskStatus;
import com.nevis.search.repository.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "app.search.threshold=0.5",
        "app.search.limit=10",
        "app.search.chunk-size=3000",
        "app.search.chunk-overlap=300",
        "app.summary.max-chars=200000",
        "app.worker.summary.max-attempts=5"
    }
)
@EnabledIfEnvironmentVariable(named = "APP_GEMINI_API_KEY", matches = ".+")
public class NevisE2ETest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void setUp() {
        jdbcClient.sql("delete from clients").update();
        jdbcClient.sql("delete from documents").update();
        jdbcClient.sql("delete from document_chunks").update();
        jdbcClient.sql("delete from document_chunk_embeddings").update();
    }


    @Test
    @DisplayName("E2E Test")
    void nevisClientIdFilterIsolationTest() {
        // 1. SETUP: Two clients with similar interests but distinct identities [cite: 28-35]
        UUID advisorId = createClient("Alexander", "Nevis", "alex@neviswealth.com", "Wealth Advisor");
        UUID clientId = createClient("Sarah", "Connor", "sarah.c@resistance.io", "AI Investor");

        // 2. SETUP: Documents with overlapping semantic content [cite: 63-66]
        // Both documents discuss "Strategy", but belong to different clients
        String alexContent = "Internal firm strategy for Nevis Wealth expansion.";
        UUID alexDocId = ingestDocument(advisorId, "Firm Strategy", alexContent);

        String sarahContent = "This document describes personal investment strategy for AI and robotics.";
        UUID sarahDocId = ingestDocument(clientId, "Personal Strategy", sarahContent);

        waitForStatus(alexDocId, DocumentTaskStatus.READY);
        waitForStatus(sarahDocId, DocumentTaskStatus.READY);

        // --- CASE 1: The "Context Switch" - Filtered Search ---
        // When searching with sarahId, we should NEVER see Alexander's documents
        String filteredUrl = String.format("/search?q=strategy&client_id=%s", clientId);
        ResponseEntity<GlobalSearchResponse> filteredRes = restTemplate.getForEntity(filteredUrl, GlobalSearchResponse.class);

        // Validate Document Isolation
        assertThat(filteredRes.getBody().documents())
            .extracting(DocumentSearchResultItem::documentId)
            .contains(sarahDocId)
            .doesNotContain(alexDocId); // CRITICAL: Alexander's doc must be filtered out [cite: 5]

        filteredUrl = String.format("/search?q=Alexander&client_id=%s", clientId);
        filteredRes = restTemplate.getForEntity(filteredUrl, GlobalSearchResponse.class);

        // Validate Client Search remains functional
        // Even with a clientId filter, searching for "Alexander" should return the client object [cite: 6]
        assertThat(filteredRes.getBody().clientMatches())
            .extracting(ClientSearchResultItem::firstName)
            .contains("Alexander");

        // --- CASE 2: Semantic Collision + Filter ---
        // Searching for "Wealth" (found in Alexander's description) but filtering for Sarah
        String collisionUrl = String.format("/search?q=Wealth&client_id=%s", clientId);
        ResponseEntity<GlobalSearchResponse> collisionRes = restTemplate.getForEntity(collisionUrl, GlobalSearchResponse.class);

        // Should find Alexander (Client) because client search is global [cite: 6]
        assertThat(collisionRes.getBody().clientMatches())
            .extracting(ClientSearchResultItem::email)
            .contains("alex@neviswealth.com");

        // --- CASE 3: Non-Existent Filter Result ---
        // Search for a term Sarah has, but use Alexander's ID
        String ghostUrl = String.format("/search?q=robotics&client_id=%s", advisorId);
        ResponseEntity<GlobalSearchResponse> ghostRes = restTemplate.getForEntity(ghostUrl, GlobalSearchResponse.class);

        // Sarah's "Personal Strategy" contains "robotics", but it's filtered out by Alexander's ID
        assertThat(ghostRes.getBody().documents()).isEmpty();
    }

    private UUID createClient(String first, String last, String email, String description) {
        Map<String, Object> request = new HashMap<>();
        request.put("first_name", first);
        request.put("last_name", last);
        request.put("email", email);
        request.put("description", description);

        ResponseEntity<ClientResponse> response = restTemplate.postForEntity("/clients", request, ClientResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }

    private UUID ingestDocument(UUID clientId, String title, String content) {
        Map<String, Object> request = Map.of("title", title, "content", content);
        String url = String.format("/clients/%s/documents", clientId);
        return restTemplate.postForEntity(url, request, DocumentResponse.class).getBody().id();
    }

    private void waitForStatus(UUID docId, DocumentTaskStatus status) {
        await().atMost(120, TimeUnit.SECONDS).pollInterval(5, TimeUnit.SECONDS).untilAsserted(() -> {
            DocumentResponse doc = restTemplate.getForEntity("/documents/" + docId, DocumentResponse.class).getBody();
            assertThat(doc.status()).isEqualTo(status);
        });
    }
}