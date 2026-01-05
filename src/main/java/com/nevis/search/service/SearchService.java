package com.nevis.search.service;

import com.nevis.search.controller.ClientSearchResponse;
import com.nevis.search.controller.DocumentSearchResponse;

import java.util.Optional;
import java.util.UUID;

public interface SearchService {

    ClientSearchResponse findClient(String query);
    DocumentSearchResponse findDocument(Optional<UUID> clientId, String query);

}
