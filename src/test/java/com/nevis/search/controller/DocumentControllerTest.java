package com.nevis.search.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nevis.search.exception.EntityNotFoundException;
import com.nevis.search.model.DocumentTaskStatus;
import com.nevis.search.service.DocumentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DocumentService documentService;

    @Test
    @DisplayName("POST /clients/{id}/documents should start ingestion and return 201")
    void createDocument_ShouldReturn201() throws Exception {
        UUID clientId = UUID.randomUUID();
        DocumentRequest request = new DocumentRequest("Title", "Content");
        DocumentResponse response = new DocumentResponse(
            UUID.randomUUID(), clientId, "Title", "Content", null, DocumentTaskStatus.PROCESSING, DocumentTaskStatus.PROCESSING, OffsetDateTime.now()
        );

        when(documentService.ingestDocument(eq("Title"), eq("Content"), eq(clientId)))
            .thenReturn(response);

        mockMvc.perform(post("/clients/{id}/documents", clientId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.client_id").value(clientId.toString()))
            .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    @DisplayName("GET /documents/{id} should return document details")
    void getDocument_ShouldReturnDetails() throws Exception {
        UUID docId = UUID.randomUUID();
        DocumentResponse response = new DocumentResponse(
            docId, UUID.randomUUID(), "Title", "Content", "Summary", DocumentTaskStatus.READY, DocumentTaskStatus.READY, OffsetDateTime.now()
        );

        when(documentService.getById(docId)).thenReturn(response);

        mockMvc.perform(get("/documents/{id}", docId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Title"))
            .andExpect(jsonPath("$.summary").value("Summary"));
    }

    @Test
    @DisplayName("GET /documents/{id} should return 404 when document does not exist")
    void getDocument_ShouldReturn404_WhenNotFound() throws Exception {
        UUID docId = UUID.randomUUID();
        String errorMessage = "Entity not found: " + docId;

        when(documentService.getById(docId))
            .thenThrow(new EntityNotFoundException(docId));

        mockMvc.perform(get("/documents/{id}", docId)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value(errorMessage))
            .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }
}