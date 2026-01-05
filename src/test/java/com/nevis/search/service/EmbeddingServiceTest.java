package com.nevis.search.service;

import com.nevis.search.exception.EmbeddingException;
import com.nevis.search.infra.RateLimiter;
import com.nevis.search.model.DocumentChunk;
import com.nevis.search.model.DocumentTaskStatus;
import com.nevis.search.repository.DocumentChunkRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmbeddingServiceTest {

    @Mock
    private DocumentService documentService;
    @Mock
    private DocumentChunkRepository chunkRepository;
    @Mock
    private EmbeddingModel embeddingModel;
    @Mock
    private ChatModel chatModel;
    @Mock
    private RateLimiter chatLimiter;
    @Mock
    private RateLimiter embeddingLimiter;

    private EmbeddingServiceImpl embeddingService;

    @BeforeEach
    void setUp() {
        embeddingService = new EmbeddingServiceImpl(
            chatLimiter,
            embeddingLimiter,
            documentService,
            chunkRepository,
            embeddingModel,
            chatModel
        );

        when(chatLimiter.execute(anyString(), anyInt(), any()))
            .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get());

        when(embeddingLimiter.execute(anyString(), anyInt(), any()))
            .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get());
    }

    @Nested
    @DisplayName("generateForDocument Tests")
    class GenerateForDocumentTests {

        @Test
        @DisplayName("Should successfully process all pending chunks")
        void shouldProcessAllPendingChunks() {
            UUID docId = UUID.randomUUID();
            DocumentChunk chunk = createChunk(docId, "Sample content");

            when(chunkRepository.countPendingByDocumentId(docId)).thenReturn(1);
            when(chunkRepository.claimNextPendingChunk(docId)).thenReturn(Optional.of(chunk));
            when(chatModel.chat(anyString())).thenReturn("Tax, KYC, AML");

            float[] vector = new float[]{0.1f, 0.2f};
            Response<List<Embedding>> mockResponse = Response.from(List.of(
                new Embedding(vector), new Embedding(vector), new Embedding(vector)
            ));
            when(embeddingModel.embedAll(anyList())).thenReturn(mockResponse);

            embeddingService.generateForDocument(docId);

            verify(documentService).saveEmbeddings(eq(docId), eq(chunk.id()), anyMap());
            verify(chunkRepository, never()).markAsFailed(any(), any());
        }

        @Test
        @DisplayName("Should handle empty terms from LLM by marking chunk as ready")
        void shouldHandleEmptyTerms() {
            UUID docId = UUID.randomUUID();
            DocumentChunk chunk = createChunk(docId, "Content");

            when(chunkRepository.countPendingByDocumentId(docId)).thenReturn(1);
            when(chunkRepository.claimNextPendingChunk(docId)).thenReturn(Optional.of(chunk));
            when(chatModel.chat(anyString())).thenReturn("");

            embeddingService.generateForDocument(docId);

            verify(chunkRepository).updateStatus(chunk.id(), DocumentTaskStatus.READY);
            verify(embeddingModel, never()).embedAll(anyList());
        }

        @Test
        @DisplayName("Should mark chunk as failed when an exception occurs")
        void shouldMarkAsFailedOnException() {
            UUID docId = UUID.randomUUID();
            DocumentChunk chunk = createChunk(docId, "Error content");

            when(chunkRepository.countPendingByDocumentId(docId)).thenReturn(1);
            when(chunkRepository.claimNextPendingChunk(docId)).thenReturn(Optional.of(chunk));
            when(chatModel.chat(anyString())).thenThrow(new RuntimeException("API Down"));

            embeddingService.generateForDocument(docId);

            verify(chunkRepository).markAsFailed(eq(chunk.id()), contains("API Down"));
        }

        @Test
        @DisplayName("Should do nothing if no pending chunks found")
        void shouldHandleNoPendingChunks() {
            UUID docId = UUID.randomUUID();
            when(chunkRepository.countPendingByDocumentId(docId)).thenReturn(0);

            embeddingService.generateForDocument(docId);

            verify(chunkRepository, never()).claimNextPendingChunk(any());
            verifyNoInteractions(chatModel, embeddingModel, documentService);
        }

        @Test
        @DisplayName("Should respect rate limits with correct token estimates")
        void shouldRespectRateLimits() {
            UUID docId = UUID.randomUUID();
            DocumentChunk chunk = createChunk(docId, "Test content");
            String terms = "Term1, Term2"; // 12 characters

            when(chunkRepository.countPendingByDocumentId(docId)).thenReturn(1);
            when(chunkRepository.claimNextPendingChunk(docId)).thenReturn(Optional.of(chunk));
            when(chatModel.chat(anyString())).thenReturn(terms);
            when(embeddingModel.embedAll(anyList())).thenReturn(Response.from(List.of(new Embedding(new float[0]), new Embedding(new float[0]))));

            embeddingService.generateForDocument(docId);

            verify(chatLimiter).execute(eq(EmbeddingServiceImpl.CHAT_LIMIT), eq(1), any());
            verify(embeddingLimiter).execute(eq(EmbeddingServiceImpl.EMBEDDING_LIMIT), anyInt(), any());
        }
    }

    @Nested
    @DisplayName("embedQuery Tests")
    class EmbedQueryTests {

        @Test
        @DisplayName("Should return vector when query is valid")
        void shouldReturnVectorForValidQuery() {
            String input = "  What is KYC?  ";
            float[] expectedVector = new float[]{0.5f, 0.5f};
            when(embeddingModel.embed(anyString())).thenReturn(Response.from(new Embedding(expectedVector)));

            float[] result = embeddingService.embedQuery(input);

            assertThat(result).isEqualTo(expectedVector);
            verify(embeddingModel).embed("what is kyc?");
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
        @DisplayName("Should truncate query if too long")
        void shouldTruncateLongQuery() {
            String longQuery = "a".repeat(1100);
            when(embeddingModel.embed(anyString())).thenReturn(Response.from(new Embedding(new float[]{0.1f})));

            embeddingService.embedQuery(longQuery);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(embeddingModel).embed(captor.capture());
            assertThat(captor.getValue()).hasSize(1000);
        }

        @Test
        @DisplayName("Should throw EmbeddingException when model returns empty vector")
        void shouldThrowExceptionWhenModelReturnsEmptyVector() {
            when(embeddingModel.embed(anyString())).thenReturn(Response.from(new Embedding(new float[0])));

            assertThatThrownBy(() -> embeddingService.embedQuery("test"))
                .isInstanceOf(EmbeddingException.class);
        }

        @Test
        @DisplayName("Should throw EmbeddingException when model call fails")
        void shouldWrapExceptionOnModelFailure() {
            when(embeddingModel.embed(anyString())).thenThrow(new RuntimeException("API Down"));

            assertThatThrownBy(() -> embeddingService.embedQuery("test"))
                .isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("Error during query vectorization");
        }

        @Test
        @DisplayName("Should sanitize input (trim and lowercase)")
        void shouldSanitizeInput() {
            String query = "  UPPERCASE query  ";
            String sanitized = "uppercase query";
            when(embeddingModel.embed(sanitized)).thenReturn(Response.from(new Embedding(new float[1])));

            embeddingService.embedQuery(query);

            verify(embeddingModel).embed(sanitized);
        }
    }

    private DocumentChunk createChunk(UUID docId, String content) {
        return new DocumentChunk(UUID.randomUUID(), docId, content, null, DocumentTaskStatus.PENDING, null, 0, null, null);
    }
}