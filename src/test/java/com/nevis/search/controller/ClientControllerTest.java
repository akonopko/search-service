package com.nevis.search.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nevis.search.config.SecurityConfig;
import com.nevis.search.exception.EntityNotFoundException;
import com.nevis.search.service.ClientService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClientController.class)
@Import(SecurityConfig.class)
@WithMockUser(username = "nevis_admin")
class ClientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ClientService clientService;

    @Test
    @DisplayName("POST /clients should return 201 Created and JSON")
    void createClient_ShouldReturn201() throws Exception {
        ClientRequest request = new ClientRequest(
            "John", "Doe", "john.doe@example.com", "Description", List.of("link1")
        );
        ClientResponse response = new ClientResponse(
            UUID.randomUUID(), "John", "Doe", "john.doe@example.com", "Description", List.of("link1")
        );

        when(clientService.create(any(ClientRequest.class))).thenReturn(response);

        mockMvc.perform(post("/clients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(response.id().toString()))
            .andExpect(jsonPath("$.first_name").value("John"));
    }

    @Test
    @DisplayName("GET /clients/{id} should return 404 if not found")
    void getClient_ShouldReturn404_WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(clientService.getById(id)).thenThrow(new EntityNotFoundException(id));

        mockMvc.perform(get("/clients/{id}", id))
            .andExpect(status().isNotFound());
    }
}