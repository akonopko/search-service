package com.nevis.search.service;

import com.nevis.search.controller.ClientRequest;
import com.nevis.search.controller.ClientResponse;

import java.util.UUID;

public interface ClientService {
    ClientResponse create(ClientRequest request);
    ClientResponse getById(UUID id);
}