package com.nevis.search.service;

import com.nevis.search.model.ClientSearchResponse;
import com.nevis.search.model.DocumentSearchResponse;

import java.util.Optional;
import java.util.UUID;

public interface SearchService {

    ClientSearchResponse findClient(String query);
    DocumentSearchResponse findDocument(Optional<UUID> clientId, String query);

}
