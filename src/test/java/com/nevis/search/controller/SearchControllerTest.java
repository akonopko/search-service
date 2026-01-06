package com.nevis.search.controller;

import com.nevis.search.config.SecurityConfig;
import com.nevis.search.model.DocumentTaskStatus;
import com.nevis.search.service.ClientService;
import com.nevis.search.service.SearchService;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SearchController.class)
@Import(SecurityConfig.class)
@WithMockUser(username = "nevis_admin")
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchService searchService;

    @MockitoBean
    private ClientService clientService;

    @Test
    @DisplayName("Should return combined search results with 200 OK")
    void globalSearch_ShouldReturnResults_WhenQueryIsProvided() throws Exception {
        // Given
        String query = "John";
        UUID clientId = UUID.randomUUID();

        var mockClient = new ClientSearchResultItem(
            UUID.randomUUID(), "John", "Doe", "john@example.com", "desc", 0.95, Arrays.asList("link1", "link2"), OffsetDateTime.now()
        );
        var mockDoc = new DocumentSearchResultItem(
            UUID.randomUUID(), clientId, "Tax Report", 0.85, "Content summary", 
            DocumentTaskStatus.READY, null
        );

        // Mocking Service responses
        when(searchService.findClient(query))
            .thenReturn(new ClientSearchResponse(List.of(mockClient), List.of()));
        
        when(searchService.findDocument(any(), eq(query)))
            .thenReturn(new DocumentSearchResponse(List.of(mockDoc)));

        // When & Then
        mockMvc.perform(get("/search")
                .param("q", query)
                .param("client_id", clientId.toString())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.clientMatches").isArray())
            .andExpect(jsonPath("$.clientMatches[0].first_name").value("John"))
            .andExpect(jsonPath("$.clientMatches[0].social_links[0]").value("link1"))
            .andExpect(jsonPath("$.clientMatches[0].social_links[1]").value("link2"))
            .andExpect(jsonPath("$.documents[0].title").value("Tax Report"))
            .andExpect(jsonPath("$.documents[0].client_id").value(clientId.toString()));
    }

    @Test
    @DisplayName("Should work without optional client_id")
    void globalSearch_ShouldWorkWithoutClientId() throws Exception {
        String query = "global";

        when(searchService.findClient(anyString()))
            .thenReturn(new ClientSearchResponse(List.of(), List.of()));
        when(searchService.findDocument(eq(Optional.empty()), anyString()))
            .thenReturn(new DocumentSearchResponse(List.of()));

        mockMvc.perform(get("/search").param("q", query))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.clientMatches").isEmpty())
            .andExpect(jsonPath("$.clientSuggestions").isEmpty())
            .andExpect(jsonPath("$.documents").isEmpty());
    }

    @Test
    @DisplayName("Should return 400 Bad Request when query 'q' is missing")
    void globalSearch_ShouldReturn400_WhenQueryIsMissing() throws Exception {
        mockMvc.perform(get("/search"))
            .andExpect(status().isBadRequest());
    }
}