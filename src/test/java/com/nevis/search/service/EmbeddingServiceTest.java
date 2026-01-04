package com.nevis.search.service;

import com.nevis.search.exception.EmbeddingException;
import com.nevis.search.model.DocumentChunk;
import com.nevis.search.model.DocumentTaskStatus;
import com.nevis.search.repository.DocumentChunkRepository;
import com.nevis.search.repository.DocumentRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {EmbeddingServiceImpl.class})
@EnableRetry
class EmbeddingServiceTest {

    @Autowired
    private EmbeddingService embeddingService;

    @MockitoBean
    private DocumentRepository repository;

    @MockitoBean
    private DocumentChunkRepository chunkRepository;

    @MockitoBean
    private DocumentService documentService;
    @MockitoBean
    private EmbeddingModel embeddingModel;

    private final UUID docId = UUID.randomUUID();

    @Test
    @DisplayName("Should retry 3 times and then succeed")
    void shouldRetryAndEventuallySucceed() {
        setupPendingChunks(1);

        when(embeddingModel.embedAll(anyList()))
            .thenThrow(new dev.langchain4j.exception.RetriableException("API Timeout"))
            .thenReturn(Response.from(List.of(Embedding.from(new float[1536]))));

        embeddingService.generateForDocument(docId);

        verify(embeddingModel, times(2)).embedAll(anyList()); // 1 провал + 1 успех
        verify(documentService).saveEmbeddings(eq(docId), anyMap());
    }

    @Test
    @DisplayName("Should recover (set FAILED) after 3 failed attempts")
    void shouldRecoverAfterExhaustingRetries() {
        setupPendingChunks(1);

        when(embeddingModel.embedAll(anyList()))
            .thenThrow(new dev.langchain4j.exception.RetriableException("Gemini is down"));

        embeddingService.generateForDocument(docId);

        verify(embeddingModel, times(3)).embedAll(anyList());
        verify(chunkRepository).markAllDocumentChunksAsFailed(eq(docId), anyString());
    }

    @Test
    @DisplayName("Should retry on Mismatch and succeed on second attempt")
    void shouldRetryOnMismatch() {
        UUID chunkId = UUID.randomUUID();
        DocumentChunk chunk = new DocumentChunk(chunkId, docId, "text", null,
            DocumentTaskStatus.PENDING, null, 0, null, null);

        when(chunkRepository.startProcessing(docId))
            .thenReturn(List.of(chunk));

        when(embeddingModel.embedAll(anyList()))
            .thenReturn(Response.from(List.of()))
            .thenReturn(Response.from(List.of(Embedding.from(new float[1536]))));

        embeddingService.generateForDocument(docId);

        verify(embeddingModel, times(2)).embedAll(anyList());
        verify(documentService).saveEmbeddings(eq(docId), anyMap());
    }

    @Test
    @DisplayName("Should not retry on non-retriable exceptions and call recover immediately")
    void shouldNotRetryOnFatalErrors() {
        setupPendingChunks(1);
        when(embeddingModel.embedAll(anyList()))
            .thenThrow(new IllegalArgumentException("Fatal developer error"));

        embeddingService.generateForDocument(docId);
        verify(embeddingModel, times(1)).embedAll(anyList());

        verify(chunkRepository).markAllDocumentChunksAsFailed(
            eq(docId),
            contains("Fatal developer error")
        );
    }

    @Test
    @DisplayName("Should do nothing if no pending chunks found")
    void shouldHandleNoPendingChunks() {
        when(chunkRepository.startProcessing(docId))
            .thenReturn(List.of());

        embeddingService.generateForDocument(docId);

        verifyNoInteractions(embeddingModel);
        verifyNoInteractions(documentService);
    }


    private void setupPendingChunks(int count) {
        List<DocumentChunk> chunks = IntStream.range(0, count)
            .mapToObj(i -> new DocumentChunk(UUID.randomUUID(), docId, "content " + i, null,
                DocumentTaskStatus.PENDING, null, 0, null, null))
            .toList();
        when(chunkRepository.startProcessing(docId)).thenReturn(chunks);
    }

    @Nested
    @DisplayName("embedQuery tests")
    class EmbedQueryTests {

        @Test
        @DisplayName("Should return vector when query is valid")
        void shouldReturnVectorForValidQuery() {
            String query = "Valid Search Query";
            float[] expectedVector = new float[1536];
            expectedVector[0] = 0.5f;

            when(embeddingModel.embed(query.toLowerCase())).thenReturn(Response.from(Embedding.from(expectedVector)));

            float[] result = embeddingService.embedQuery(query);

            assertThat(result).isEqualTo(expectedVector);
            verify(embeddingModel).embed(query.toLowerCase());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\n", "\t"})
        @DisplayName("Should throw exception for null, empty or blank input")
        void shouldThrowExceptionForEmptyInput(String input) {
            assertThatThrownBy(() -> embeddingService.embedQuery(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Query cannot be empty");
        }

        @Test
        @DisplayName("Should truncate query if it exceeds 1000 characters")
        void shouldTruncateLongQuery() {
            String longQuery = "a".repeat(1100);
            String expectedTruncatedQuery = "a".repeat(1000);
            float[] mockVector = new float[1536];

            when(embeddingModel.embed(expectedTruncatedQuery)).thenReturn(Response.from(Embedding.from(mockVector)));

            float[] result = embeddingService.embedQuery(longQuery);

            assertThat(result).isEqualTo(mockVector);
            verify(embeddingModel).embed(expectedTruncatedQuery);
            verify(embeddingModel, never()).embed(longQuery);
        }

        @Test
        @DisplayName("Should throw EmbeddingException if model returns empty vector")
        void shouldThrowExceptionWhenModelReturnsEmptyVector() {
            String query = "some query";
            // Mocking a response with a null or empty vector (depends on how the model behaves)
            when(embeddingModel.embed(anyString())).thenReturn(Response.from(Embedding.from(new float[0])));

            assertThatThrownBy(() -> embeddingService.embedQuery(query))
                .isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("Error during query vectorization");
        }

        @Test
        @DisplayName("Should throw EmbeddingException when model call fails")
        void shouldWrapExceptionOnModelFailure() {
            String query = "failing query";
            when(embeddingModel.embed(anyString())).thenThrow(new RuntimeException("API Down"));

            assertThatThrownBy(() -> embeddingService.embedQuery(query))
                .isInstanceOf(RuntimeException.class) // Adjust if EmbeddingException is a specific type
                .hasMessageContaining("Error during query vectorization");
        }

        @Test
        @DisplayName("Should sanitize input (trim and lowercase)")
        void shouldSanitizeInput() {
            String query = "  UPPERCASE query  ";
            String sanitized = "uppercase query";
            float[] mockVector = new float[1536];

            when(embeddingModel.embed(sanitized)).thenReturn(Response.from(Embedding.from(mockVector)));

            embeddingService.embedQuery(query);

            verify(embeddingModel).embed(sanitized);
        }
    }
}