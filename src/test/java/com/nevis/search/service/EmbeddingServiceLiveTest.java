package com.nevis.search.service;

import com.nevis.search.config.LangChainConfig;
import com.nevis.search.model.DocumentChunk;
import com.nevis.search.model.DocumentTaskStatus;
import com.nevis.search.repository.DocumentRepository;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest(
    classes = {EmbeddingServiceImpl.class, LangChainConfig.class},
    properties = "app.gemini.api-key=${GEMINI_API_KEY}")
@Tag("live-integration")
@EnabledIfEnvironmentVariable(named = "APP_GEMINI_API_KEY", matches = ".+")
class EmbeddingServiceLiveTest {

    @Autowired
    private EmbeddingModel embeddingModel;

    @MockitoBean
    private DocumentRepository repository;

    @MockitoBean
    private DocumentService documentService;

    @Autowired
    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        embeddingService = new EmbeddingServiceImpl(documentService, repository, embeddingModel);
    }

    @Test
    @DisplayName("Real API Call: Should receive vectors from Gemini")
    void shouldGetRealVectorsFromGemini() {
        UUID docId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        String content = "Spring Boot project with Gemini";

        DocumentChunk mockChunk = new DocumentChunk(chunkId, docId, content, null,
            DocumentTaskStatus.PENDING, null, 0, null, null);

        when(repository.findChunksByStatusDocId(docId, DocumentTaskStatus.PENDING))
            .thenReturn(List.of(mockChunk));

        embeddingService.generateForDocument(docId);

        ArgumentCaptor<Map<UUID, float[]>> captor = ArgumentCaptor.forClass(Map.class);
        verify(documentService).saveEmbeddings(eq(docId), captor.capture());

        Map<UUID, float[]> result = captor.getValue();

        assertThat(result).containsKey(chunkId);
        float[] vector = result.get(chunkId);

        assertThat(vector).isNotEmpty();
        System.out.println("Dimension of Gemini vector: " + vector.length);
        assertThat(vector.length).isGreaterThan(0);
    }
}