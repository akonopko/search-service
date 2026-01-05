package com.nevis.search.worker;

import com.nevis.search.event.DocumentRetryEvent;
import com.nevis.search.repository.DocumentChunkRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChunkMaintenanceWorkerTest {

    @Mock
    private DocumentChunkRepository chunkRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ChunkMaintenanceWorker worker;

    @Test
    @DisplayName("Should deduplicate document IDs and publish exactly one event per document")
    void shouldDeduplicateAndPublishEvents() {
        UUID docId1 = UUID.randomUUID();
        UUID docId2 = UUID.randomUUID();
        List<UUID> affectedIds = List.of(docId1, docId1, docId2);
        
        when(chunkRepository.resetStaleAndFailedChunks()).thenReturn(affectedIds);

        worker.cleanupStaleChunks();

        verify(eventPublisher, times(1)).publishEvent(new DocumentRetryEvent(docId1));
        verify(eventPublisher, times(1)).publishEvent(new DocumentRetryEvent(docId2));
        verify(eventPublisher, times(2)).publishEvent(any(DocumentRetryEvent.class));
    }

    @Test
    @DisplayName("Should not publish any events if no chunks were reset")
    void shouldDoNothingWhenNoChunksAffected() {
        when(chunkRepository.resetStaleAndFailedChunks()).thenReturn(List.of());
        worker.cleanupStaleChunks();
        verifyNoInteractions(eventPublisher);
    }
}