package com.nevis.search.service;

import com.nevis.search.model.DocumentChunk;
import com.nevis.search.repository.DocumentChunkRepository;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SummaryServiceImpl implements SummaryService {


    @Override
    @Transactional
    public void generateForDocument(UUID docId) {
    }

}
