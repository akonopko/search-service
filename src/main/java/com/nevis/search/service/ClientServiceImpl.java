package com.nevis.search.service;

import com.nevis.search.controller.ClientRequest;
import com.nevis.search.controller.ClientResponse;
import com.nevis.search.exception.EntityNotFoundException;
import com.nevis.search.model.Client;
import com.nevis.search.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientServiceImpl implements ClientService {

    private final ClientRepository clientRepository;

    @Override
    @Transactional
    public ClientResponse create(ClientRequest request) {
        log.debug("Processing request to create client: {} {}", request.firstName(), request.lastName());

        Client clientToSave = new Client(
            null,
            request.firstName(),
            request.lastName(),
            request.email(),
            request.description(),
            request.socialLinks(),
            null,
            null
        );

        Client savedClient = clientRepository.save(clientToSave);
        log.info("Successfully created client with ID: {}", savedClient.id());
        return mapToResponse(savedClient);
    }

    @Override
    @Transactional(readOnly = true)
    public ClientResponse getById(UUID id) {
        log.debug("Attempting to find client by ID: {}", id);

        return clientRepository.findById(id)
            .map(this::mapToResponse)
            .orElseThrow(() -> {
                log.warn("Client not found for ID: {}", id);
                return new EntityNotFoundException(id);
            });
    }

    /**
     * Helper method to transform Domain Model to API Response.
     * This keeps the mapping logic central and easy to maintain.
     */
    private ClientResponse mapToResponse(Client client) {
        return new ClientResponse(
            client.id(),
            client.firstName(),
            client.lastName(),
            client.email(),
            client.description(),
            client.socialLinks()
        );
    }
}