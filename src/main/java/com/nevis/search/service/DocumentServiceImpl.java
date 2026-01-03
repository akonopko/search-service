package com.nevis.search.service;

import com.nevis.search.model.Document;
import com.nevis.search.model.DocumentTaskStatus;
import com.nevis.search.repository.DocumentRepository;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Collections.emptyList;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository repository;
    private final DocumentSplitter splitter = DocumentSplitters.recursive(3000, 300);

    @Transactional
    public Document ingestDocument(String title, String content, UUID clientId) {
        Document doc = new Document(null, clientId, title, content, null, DocumentTaskStatus.PENDING, null, null);

        Document savedDoc = repository.save(doc);

        List<TextSegment> segments = getSplittedChunks(content);
        if (segments.isEmpty()) {
            repository.updateDocumentStatus(savedDoc.id(), DocumentTaskStatus.READY);
        } else {
            repository.saveChunks(savedDoc.id(), segments);
            repository.updateDocumentStatus(savedDoc.id(), DocumentTaskStatus.PROCESSING);
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
    public void saveEmbeddings(UUID docId, Map<UUID, float[]> embeddingMap) {
        if (embeddingMap == null || embeddingMap.isEmpty()) {
            log.warn("Doc {}: saveEmbeddings called with empty data", docId);
            return;
        }

        log.info("Doc {}: Updating {} chunk embeddings in database", docId, embeddingMap.size());

        embeddingMap.forEach(repository::updateEmbeddingChunkVector);

        if (repository.areAllChunksProcessed(docId)) {
            log.info("Doc {}: Updating status to Ready", docId);
            repository.updateDocumentStatus(docId, DocumentTaskStatus.READY);
        }
    }

}