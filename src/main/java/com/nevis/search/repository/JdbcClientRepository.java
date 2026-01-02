package com.nevis.search.repository;

import com.nevis.search.model.Client;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcClientRepository implements ClientRepository {


    @Override
    public Client save(Client client) {
        return null;
    }

    @Override
    public Optional<Client> findById(UUID id) {
        return null;
    }
}