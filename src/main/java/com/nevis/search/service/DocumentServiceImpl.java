package com.nevis.search.service;

import com.nevis.search.controller.DocumentResponse;
import com.nevis.search.event.DocumentIngestedEvent;
import com.nevis.search.exception.EntityNotFoundException;
import com.nevis.search.model.Document;
import com.nevis.search.controller.DocumentSearchResultItem;
import com.nevis.search.model.DocumentTaskStatus;
import com.nevis.search.repository.DocumentChunkRepository;
import com.nevis.search.repository.DocumentRepository;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.util.Collections.emptyList;

@Service
@Slf4j
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentSplitter splitter;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.worker.embeddings.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.worker.embeddings.stale-threshold-minutes:5}")
    private int staleThresholdMinutes;

    @Value("${app.document.similarity-threshold:0.72}")
    private double documentSimilarityThreshold;

    public DocumentServiceImpl(
        DocumentRepository documentRepository,
        DocumentChunkRepository chunkRepository,
        ApplicationEventPublisher eventPublisher,
        @Value("${app.search.chunk-size:3000}") int chunkSize,
        @Value("${app.search.chunk-overlap:300}") int chunkOverlap
    ) {
        this.documentRepository = documentRepository;
        this.splitter = DocumentSplitters.recursive(chunkSize, chunkOverlap);
        this.eventPublisher = eventPublisher;
        this.chunkRepository = chunkRepository;
    }


    @Override
    @Transactional
    public DocumentResponse ingestDocument(String title, String content, UUID clientId) {
        log.debug("Ingesting document for client {}: {}", clientId, title);

        Document doc = new Document(
            null,
            clientId,
            title,
            content,
            null,
            DocumentTaskStatus.PENDING,
            null,
            0,
            DocumentTaskStatus.PENDING,
            null,
            null
        );

        Document savedDoc = documentRepository.save(doc);

        List<TextSegment> segments = getSplittedChunks(content);

        if (segments.isEmpty()) {
            documentRepository.updateStatus(savedDoc.id(), DocumentTaskStatus.READY);

            savedDoc = new Document(
                savedDoc.id(),
                savedDoc.clientId(),
                savedDoc.title(),
                savedDoc.content(),
                savedDoc.summary(),
                savedDoc.summaryStatus(),
                savedDoc.summaryErrorMessage(),
                savedDoc.summaryAttempts(),
                DocumentTaskStatus.READY,
                savedDoc.createdAt(),
                savedDoc.updatedAt()
            );
        } else {
            chunkRepository.saveChunks(savedDoc.id(), segments);
            documentRepository.updateStatus(savedDoc.id(), DocumentTaskStatus.PROCESSING);

            eventPublisher.publishEvent(new DocumentIngestedEvent(savedDoc.id()));

            savedDoc = new Document(
                savedDoc.id(),
                savedDoc.clientId(),
                savedDoc.title(),
                savedDoc.content(),
                savedDoc.summary(),
                savedDoc.summaryStatus(),
                savedDoc.summaryErrorMessage(),
                savedDoc.summaryAttempts(),
                DocumentTaskStatus.PROCESSING,
                savedDoc.createdAt(),
                savedDoc.updatedAt()
            );
        }
        return mapToResponse(savedDoc);
    }

    private DocumentResponse mapToResponse(Document doc) {
        return new DocumentResponse(
            doc.id(),
            doc.clientId(),
            doc.title(),
            doc.content(),
            doc.summary(),
            doc.summaryStatus(),
            doc.status(),
            doc.createdAt()
        );
    }

    private List<TextSegment> getSplittedChunks(String content) {
        if (content == null || content.isBlank()) {
            return emptyList();
        }
        return splitter.split(
            dev.langchain4j.data.document.Document.from(content)
        );
    }

    @Transactional
    public void saveEmbeddings(UUID docId, UUID chunkId, Map<String, float[]> embeddingMap) {
        if (embeddingMap == null || embeddingMap.isEmpty()) {
            log.warn("Doc {}: saveEmbeddings called with empty data", docId);
            return;
        }

        log.info("Doc {}: Inserting {} chunk embeddings in database", docId, embeddingMap.size());

        embeddingMap.forEach((term, vector) ->
            chunkRepository.insertChunkVector(docId, chunkId, term, vector));

        chunkRepository.updateStatus(chunkId, DocumentTaskStatus.READY);

        if (chunkRepository.areAllChunksProcessed(docId)) {
            log.info("Doc {}: Updating status to Ready", docId);
            documentRepository.updateStatus(docId, DocumentTaskStatus.READY);
        }
    }

    public List<DocumentSearchResultItem> search(float[] queryVector, Optional<Integer> limit, Optional<UUID> clientId) {
        return chunkRepository.findSimilar(queryVector, limit, clientId, documentSimilarityThreshold);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse getById(UUID id) {
        log.debug("Fetching document by ID: {}", id);

        return documentRepository.findById(id)
            .map(this::mapToResponse)
            .orElseThrow(() -> {
                log.warn("Document not found with ID: {}", id);
                return new EntityNotFoundException(id);
            });
    }


}