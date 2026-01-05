package com.nevis.search.service;

import com.nevis.search.infra.RateLimiter;
import com.nevis.search.model.Document;
import com.nevis.search.model.DocumentTaskStatus;
import com.nevis.search.repository.DocumentRepository;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static com.nevis.search.service.EmbeddingServiceImpl.CHAT_LIMIT;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SummaryGeneratorServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private ChatModel chatModel;

    @Mock
    private RateLimiter chatLimiter;

    private SummaryGeneratorServiceImpl summaryGeneratorService;

    private final UUID docId = UUID.randomUUID();
    private final int maxAttempts = 5;

    @BeforeEach
    void setUp() {
        summaryGeneratorService = new SummaryGeneratorServiceImpl(documentRepository, chatModel, chatLimiter);
        
        ReflectionTestUtils.setField(summaryGeneratorService, "maxSummaryChars", 200000);
        ReflectionTestUtils.setField(summaryGeneratorService, "maxAttempts", maxAttempts);

        // Configure the rate limiter to execute the lambda passed to it
        lenient().when(chatLimiter.execute(anyString(), anyInt(), any()))
            .thenAnswer(invocation -> {
                java.util.function.Supplier<?> supplier = invocation.getArgument(2);
                return supplier.get();
            });
    }

    @Test
    @DisplayName("Should handle empty content by marking status as READY with a note")
    void shouldHandleEmptyContent() {
        Document mockDoc = createMockDocument("");
        
        when(documentRepository.claimForSummary(eq(docId), eq(maxAttempts))).thenReturn(Optional.of(mockDoc));

        summaryGeneratorService.generateSummary(docId);

        verify(documentRepository).updateSummaryStatus(eq(docId), eq(DocumentTaskStatus.READY), contains("Empty content"));
        verifyNoInteractions(chatModel);
    }

    @Test
    @DisplayName("Should mark as FAILED when LLM call throws an exception")
    void shouldHandleExceptionDuringGeneration() {
        Document mockDoc = createMockDocument("Valid content");
        
        when(documentRepository.claimForSummary(eq(docId), eq(maxAttempts))).thenReturn(Optional.of(mockDoc));
        when(chatModel.chat(anyString())).thenThrow(new RuntimeException("Gemini Timeout"));

        summaryGeneratorService.generateSummary(docId);

        verify(documentRepository).updateSummaryStatus(eq(docId), eq(DocumentTaskStatus.FAILED), eq("Gemini Timeout"));
    }

    @Test
    @DisplayName("Should do nothing if claimForSummary returns empty")
    void shouldDoNothingIfCannotClaim() {
        when(documentRepository.claimForSummary(eq(docId), eq(maxAttempts))).thenReturn(Optional.empty());

        summaryGeneratorService.generateSummary(docId);

        verify(documentRepository, never()).updateSummary(any(), any(), any());
        verify(documentRepository, never()).updateSummaryStatus(any(), any(), any());
        verifyNoInteractions(chatModel);
    }

    @Test
    @DisplayName("Should successfully generate summary when document is claimed")
    void shouldGenerateSummarySuccessfully() {
        Document mockDoc = createMockDocument("Large financial report content...");

        when(documentRepository.claimForSummary(eq(docId), eq(maxAttempts))).thenReturn(Optional.of(mockDoc));

        // FIX: Cast any() to String to resolve ambiguity
        when(chatModel.chat((String) any())).thenReturn("This is a summary.");

        summaryGeneratorService.generateSummary(docId);

        verify(chatLimiter).execute(eq(CHAT_LIMIT), eq(1), any());

        // FIX: Cast here as well
        verify(chatModel).chat((String) any());

        verify(documentRepository).updateSummary(eq(docId), eq("This is a summary."), eq(DocumentTaskStatus.READY));
    }

    @Test
    @DisplayName("Should truncate content to maxSummaryChars before calling LLM")
    void shouldTruncateContent() {
        ReflectionTestUtils.setField(summaryGeneratorService, "maxSummaryChars", 10);
        Document mockDoc = createMockDocument("Content that is too long");

        when(documentRepository.claimForSummary(eq(docId), eq(maxAttempts))).thenReturn(Optional.of(mockDoc));

        when(chatModel.chat((String) any())).thenReturn("Sum");
        summaryGeneratorService.generateSummary(docId);

        verify(chatModel).chat((String) argThat(prompt -> ((String)prompt).contains("Content th")));
    }

    private Document createMockDocument(String content) {
        return new Document(
            docId,
            UUID.randomUUID(),
            "Title",
            content,
            null,
            DocumentTaskStatus.PENDING,
            null,
            0,
            DocumentTaskStatus.PENDING,
            null,
            null
        );
    }
}