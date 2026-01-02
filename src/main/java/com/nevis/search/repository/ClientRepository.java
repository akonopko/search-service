package com.nevis.search.repository;

import com.nevis.search.model.Client;
import com.nevis.search.model.SearchResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientRepository {
    Client save(Client client);
    Optional<Client> findById(UUID id);
    SearchResponse search(String query);
}