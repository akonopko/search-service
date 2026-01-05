package com.nevis.search.service;

import com.nevis.search.event.DocumentIngestedEvent;
import com.nevis.search.model.Document;
import com.nevis.search.model.DocumentTaskStatus;
import com.nevis.search.repository.DocumentChunkRepository;
import com.nevis.search.repository.DocumentRepository;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.util.Collections.emptyList;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentSplitter splitter = DocumentSplitters.recursive(3000, 300);
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Document ingestDocument(String title, String content, UUID clientId) {
        Document doc = new Document(null, clientId, title, content, null, DocumentTaskStatus.PENDING, null, null);

        Document savedDoc = documentRepository.save(doc);

        List<TextSegment> segments = getSplittedChunks(content);
        if (segments.isEmpty()) {
            documentRepository.updateStatus(savedDoc.id(), DocumentTaskStatus.READY);
        } else {
            chunkRepository.saveChunks(savedDoc.id(), segments);
            documentRepository.updateStatus(savedDoc.id(), DocumentTaskStatus.PROCESSING);
            eventPublisher.publishEvent(new DocumentIngestedEvent(savedDoc.id()));
        }

        return savedDoc;
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

        log.info("Doc {}: Updating {} chunk embeddings in database", docId, embeddingMap.size());

        embeddingMap.forEach((term, vector) ->
            chunkRepository.insertChunkVector(docId, chunkId, term, vector));

        chunkRepository.updateStatus(chunkId, DocumentTaskStatus.READY);

        if (chunkRepository.areAllChunksProcessed(docId)) {
            log.info("Doc {}: Updating status to Ready", docId);
            documentRepository.updateStatus(docId, DocumentTaskStatus.READY);
        }
    }

    public List<DocumentSearchResult> search(float[] queryVector, int limit, Optional<UUID> clientId) {
        return chunkRepository.findSimilar(queryVector, limit, clientId);
    }

}