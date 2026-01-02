package com.nevis.search.service;

import com.nevis.search.event.DocumentIngestedEvent;
import com.nevis.search.model.Document;
import com.nevis.search.model.DocumentTaskStatus;
import com.nevis.search.repository.DocumentRepository;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentServiceTest {

    private final DocumentRepository repository = Mockito.mock(DocumentRepository.class);
    private final DocumentService documentService = new DocumentServiceImpl(repository);

    @Test
    @DisplayName("Should split real technical text into logical chunks with overlap")
    void shouldSplitContentLogically() {
        String chunk = """
        Chapter 1: The Basics. Java is a versatile language.
        Chapter 2: Advanced AI. Integrating LLMs into Java applications in 2026 
        requires a deep understanding of vector databases and embedding models.
        """;
        String content = chunk.repeat(50);

        UUID clientId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        Document mockDoc = new Document(docId, clientId, "Test", content, null, DocumentTaskStatus.PENDING, null, null);

        when(repository.save(any(Document.class))).thenReturn(mockDoc);

        Document result = documentService.ingestDocument("Test", content, clientId);

        ArgumentCaptor<List<TextSegment>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveChunks(eq(docId), captor.capture());

        List<TextSegment> segments = captor.getValue();

        assertThat(segments).isNotEmpty();
        assertThat(segments).allMatch(segment -> segment.text().length() <= 3000);

        String firstChunk = segments.get(0).text().trim();
        String secondChunk = segments.get(1).text().trim();

        String overlapProbe = firstChunk.substring(firstChunk.length() - 50);
        assertThat(secondChunk).contains(overlapProbe);

        assertThat(firstChunk).matches("(?s).*[.!?\\n]$");
    }

    @Test
    @DisplayName("Should handle empty content without crashing")
    void shouldHandleEmptyContent() {
        String content = "";
        UUID clientId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        Document mockDoc = new Document(docId, clientId, "Empty", content, null, DocumentTaskStatus.PENDING, null, null);
        when(repository.save(any(Document.class))).thenReturn(mockDoc);

        Document result = documentService.ingestDocument("Empty", content, clientId);

        assertThat(result).isNotNull();
        verify(repository, never()).saveChunks(any(), any());
    }

    @Test
    @DisplayName("Should handle very short content that fits in one chunk")
    void shouldHandleShortContent() {
        String content = "Short text.";
        UUID clientId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        Document mockDoc = new Document(docId, clientId, "Short", content, null, DocumentTaskStatus.PENDING, null, null);
        when(repository.save(any(Document.class))).thenReturn(mockDoc);

        documentService.ingestDocument("Short", content, clientId);

        ArgumentCaptor<List<TextSegment>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveChunks(eq(docId), captor.capture());

        List<TextSegment> segments = captor.getValue();
        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).text()).isEqualTo(content);
    }

    @Test
    @DisplayName("Should handle content that is exactly the size of one chunk")
    void shouldHandleExactChunkSize() {
        String content = "A".repeat(3000);
        UUID clientId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        Document mockDoc = new Document(docId, clientId, "Exact", content, null, DocumentTaskStatus.PENDING, null, null);
        when(repository.save(any(Document.class))).thenReturn(mockDoc);

        documentService.ingestDocument("Exact", content, clientId);

        ArgumentCaptor<List<TextSegment>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveChunks(eq(docId), captor.capture());

        assertThat(captor.getValue()).hasSize(1);
    }

    @Test
    @DisplayName("Should handle content with only newlines and spaces")
    void shouldHandleWhitespaceOnlyContent() {
        String content = "\n  \n  \t  \n";
        UUID clientId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        Document mockDoc = new Document(docId, clientId, "Whitespace", content, null, DocumentTaskStatus.PENDING, null, null);
        when(repository.save(any(Document.class))).thenReturn(mockDoc);

        documentService.ingestDocument("Whitespace", content, clientId);

        verify(repository, never()).saveChunks(any(), any());
    }

    @Test
    @DisplayName("Should handle content with large non-splittable segments")
    void shouldHandleLargeIndivisibleWords() {
        String content = "A".repeat(4000);
        UUID clientId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        Document mockDoc = new Document(docId, clientId, "LargeWord", content, null, DocumentTaskStatus.PENDING, null, null);
        when(repository.save(any(Document.class))).thenReturn(mockDoc);

        documentService.ingestDocument("LargeWord", content, clientId);

        ArgumentCaptor<List<TextSegment>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveChunks(eq(docId), captor.capture());

        assertThat(captor.getValue()).hasSizeGreaterThan(1);
    }

}