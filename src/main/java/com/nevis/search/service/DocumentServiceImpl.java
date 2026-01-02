package com.nevis.search.service;

import com.nevis.search.model.Document;
import com.nevis.search.model.DocumentTaskStatus;
import com.nevis.search.repository.DocumentRepository;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository repository;
    private final DocumentSplitter splitter = DocumentSplitters.recursive(3000, 300);

    @Transactional
    public Document ingestDocument(String title, String content, UUID clientId) {
        Document doc = new Document(null, clientId, title, content, null, DocumentTaskStatus.PENDING, null, null);

        Document savedDoc = repository.save(doc);

        if (content != null && !content.isBlank()) {
            List<TextSegment> segments = splitter.split(
                dev.langchain4j.data.document.Document.from(content)
            );

            repository.saveChunks(savedDoc.id(), segments);
        }

        return savedDoc;
    }
}