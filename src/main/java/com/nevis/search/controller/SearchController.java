package com.nevis.search.controller;

import com.nevis.search.service.ClientService;
import com.nevis.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final ClientService clientService;

    @GetMapping
    public ResponseEntity<GlobalSearchResponse> globalSearch(
        @RequestParam(name = "q") String query,
        @RequestParam(name = "client_id", required = false) UUID clientId) {

        var clientResults = searchService.findClient(query);
        if (clientId != null) {
            clientService.getById(clientId);
        }

        var documentResults = searchService.findDocument(Optional.ofNullable(clientId), query);

        return ResponseEntity.ok(new GlobalSearchResponse(
            clientResults.matches(),
            clientResults.suggestions(),
            documentResults.documents()
        ));
    }
}