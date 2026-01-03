package com.nevis.search.service;

import com.nevis.search.model.Document;
import com.nevis.search.model.DocumentTaskStatus;
import com.nevis.search.repository.DocumentChunkRepository;
import com.nevis.search.repository.DocumentRepository;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentServiceTest {

    private final DocumentRepository repository = Mockito.mock(DocumentRepository.class);
    private final DocumentChunkRepository chunkRepository = Mockito.mock(DocumentChunkRepository.class);
    private final DocumentService documentService = new DocumentServiceImpl(repository, chunkRepository);

    @Nested
    @DisplayName("Split document to chunks")
    class DocumentSplitTest {

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
            verify(chunkRepository).saveChunks(eq(docId), captor.capture());

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
        @DisplayName("Should handle content that is exactly the size of one chunk")
        void shouldHandleExactChunkSize() {
            String content = "A".repeat(3000);
            UUID clientId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();

            Document mockDoc = new Document(docId, clientId, "Exact", content, null, DocumentTaskStatus.PENDING, null, null);
            when(repository.save(any(Document.class))).thenReturn(mockDoc);

            documentService.ingestDocument("Exact", content, clientId);

            ArgumentCaptor<List<TextSegment>> captor = ArgumentCaptor.forClass(List.class);
            verify(chunkRepository).saveChunks(eq(docId), captor.capture());

            assertThat(captor.getValue()).hasSize(1);
        }

        @Test
        @DisplayName("Should handle empty content and set status to READY")
        void shouldHandleEmptyContent() {
            String content = "";
            UUID clientId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();

            Document mockDoc = new Document(docId, clientId, "Empty", content, null, DocumentTaskStatus.PENDING, null, null);
            when(repository.save(any(Document.class))).thenReturn(mockDoc);

            documentService.ingestDocument("Empty", content, clientId);

            verify(chunkRepository, never()).saveChunks(any(), any());
            verify(repository).updateStatus(docId, DocumentTaskStatus.READY);
        }

        @Test
        @DisplayName("Should handle short content and set status to PROCESSING")
        void shouldHandleShortContent() {
            String content = "Short text.";
            UUID clientId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();

            Document mockDoc = new Document(docId, clientId, "Short", content, null, DocumentTaskStatus.PENDING, null, null);
            when(repository.save(any(Document.class))).thenReturn(mockDoc);

            documentService.ingestDocument("Short", content, clientId);

            verify(chunkRepository).saveChunks(eq(docId), anyList());
            verify(repository).updateStatus(docId, DocumentTaskStatus.PROCESSING);
        }

        @Test
        @DisplayName("Should handle whitespace only content and set status to READY")
        void shouldHandleWhitespaceOnlyContent() {
            String content = "\n  \n  \t  \n";
            UUID clientId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();

            Document mockDoc = new Document(docId, clientId, "Whitespace", content, null, DocumentTaskStatus.PENDING, null, null);
            when(repository.save(any(Document.class))).thenReturn(mockDoc);

            documentService.ingestDocument("Whitespace", content, clientId);

            verify(chunkRepository, never()).saveChunks(any(), any());
            verify(repository).updateStatus(docId, DocumentTaskStatus.READY);
        }

        @Test
        @DisplayName("Should handle large words by splitting and setting PROCESSING")
        void shouldHandleLargeIndivisibleWords() {
            String content = "A".repeat(4000);
            UUID clientId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();

            Document mockDoc = new Document(docId, clientId, "LargeWord", content, null, DocumentTaskStatus.PENDING, null, null);
            when(repository.save(any(Document.class))).thenReturn(mockDoc);

            documentService.ingestDocument("LargeWord", content, clientId);

            verify(repository).updateStatus(docId, DocumentTaskStatus.PROCESSING);
        }
    }

    @Nested
    @DisplayName("Save embeddings")
    class DocumentSaveEmbeddingsTest {

        private final UUID docId = UUID.randomUUID();

        @Test
        @DisplayName("Should return early if embedding map is null or empty")
        void saveEmbeddings_EmptyMap_DoesNothing() {
            documentService.saveEmbeddings(docId, null);
            documentService.saveEmbeddings(docId, Map.of());

            verify(chunkRepository, never()).updateVector(any(), any());
            verify(repository, never()).updateStatus(any(), any());
        }

        @Test
        @DisplayName("Should update chunks but NOT set READY if some chunks are still processing")
        void saveEmbeddings_NotAllFinished_OnlyUpdatesChunks() {
            UUID chunk1 = UUID.randomUUID();
            Map<UUID, float[]> embeddings = Map.of(chunk1, new float[]{0.1f});

            when(chunkRepository.areAllChunksProcessed(docId)).thenReturn(false);
            documentService.saveEmbeddings(docId, embeddings);

            verify(chunkRepository).updateVector(eq(chunk1), any(float[].class));
            verify(repository, never()).updateStatus(any(), any());
        }

        @Test
        @DisplayName("Should update document to READY when all chunks are processed")
        void saveEmbeddings_AllFinished_SetsDocumentToReady() {
            UUID chunk1 = UUID.randomUUID();
            UUID chunk2 = UUID.randomUUID();
            Map<UUID, float[]> embeddings = Map.of(
                chunk1, new float[]{0.1f},
                chunk2, new float[]{0.2f}
            );

            when(chunkRepository.areAllChunksProcessed(docId)).thenReturn(true);

            documentService.saveEmbeddings(docId, embeddings);

            verify(chunkRepository).updateVector(eq(chunk1), any());
            verify(chunkRepository).updateVector(eq(chunk2), any());

            verify(repository).updateStatus(docId, DocumentTaskStatus.READY);
        }
    }

}