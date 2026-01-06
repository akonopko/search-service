package com.nevis.search.service;

import com.nevis.search.controller.ClientRequest;
import com.nevis.search.controller.ClientResponse;
import com.nevis.search.controller.ClientSearchResponse;

import java.util.Optional;
import java.util.UUID;

public interface ClientService {
    ClientResponse create(ClientRequest request);
    ClientResponse getById(UUID id);
    ClientSearchResponse search(String query, Optional<Integer> limit, Optional<Double> similarity);
}