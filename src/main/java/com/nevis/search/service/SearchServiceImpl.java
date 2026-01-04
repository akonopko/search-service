package com.nevis.search.service;

import com.nevis.search.model.ClientSearchResponse;
import com.nevis.search.model.DocumentSearchResponse;
import com.nevis.search.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchServiceImpl implements SearchService {

    @Override
    public ClientSearchResponse findClient(String query) {
        return null;
    }

    @Override
    public DocumentSearchResponse findDocument(Optional<UUID> clientId, String query) {
        return null;
    }
}
