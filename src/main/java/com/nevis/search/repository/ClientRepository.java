package com.nevis.search.repository;

import com.nevis.search.model.Client;
import java.util.Optional;
import java.util.UUID;

public interface ClientRepository {
    Client save(Client client);
    Optional<Client> findById(UUID id);
}