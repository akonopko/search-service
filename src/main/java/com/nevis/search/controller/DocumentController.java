package com.nevis.search.controller;

import com.nevis.search.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/clients/{id}/documents")
    public ResponseEntity<DocumentResponse> createDocument(
        @PathVariable UUID id,
        @Valid @RequestBody DocumentRequest request) {

        DocumentResponse response = documentService.ingestDocument(
            request.title(),
            request.content(),
            id
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable UUID id) {
        DocumentResponse response = documentService.getById(id);
        return ResponseEntity.ok(response);
    }
}