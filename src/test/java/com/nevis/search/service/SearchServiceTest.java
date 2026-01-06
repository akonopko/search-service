package com.nevis.search.service;

import com.nevis.search.controller.ClientSearchResponse;
import com.nevis.search.controller.ClientSearchResultItem;
import com.nevis.search.controller.DocumentSearchResponse;
import com.nevis.search.controller.DocumentSearchResultItem;
import com.nevis.search.exception.WrongQueryException;
import com.nevis.search.model.*;
import com.nevis.search.repository.ClientRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private ClientService clientService;

    @Mock
    private DocumentService documentService;

    @Mock
    private EmbeddingService embeddingService;

    @InjectMocks
    private SearchServiceImpl searchService;

    @Nested
    @DisplayName("findClient tests")
    class ClientSearchTests {

        @Test
        void shouldReturnClientsWhenQueryIsValid() {
            String query = "John Doe";
            ClientSearchResultItem mockClient = mock(ClientSearchResultItem.class);
            ClientSearchResponse johnDoe = new ClientSearchResponse(List.of(mockClient), Collections.emptyList());
            when(clientService.search(query, Optional.empty(), Optional.empty()))
                .thenReturn(johnDoe);

            ClientSearchResponse response = searchService.findClient(query);

            assertThat(response.matches()).isNotEmpty();
            verify(clientService).search(query, Optional.empty(), Optional.empty());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "a", "ab"})
        void shouldThrowExceptionWhenQueryIsTooShort(String invalidQuery) {
            assertThatThrownBy(() -> searchService.findClient(invalidQuery))
                .isInstanceOf(WrongQueryException.class);
        }

        @Test
        void shouldThrowExceptionWhenQueryExceedsMaxLength() {
            String longQuery = "a".repeat(501);
            assertThatThrownBy(() -> searchService.findClient(longQuery))
                .isInstanceOf(WrongQueryException.class);
        }
    }

    @Nested
    @DisplayName("findDocument tests")
    class DocumentSearchTests {

        private final UUID clientId = UUID.randomUUID();

        @Test
        void shouldReturnDocumentsOnSemanticMatch() {
            String query = "address proof";
            float[] vector = new float[]{0.1f, 0.9f};
            DocumentSearchResultItem match = new DocumentSearchResultItem(UUID.randomUUID(), UUID.randomUUID(), "Utility Bill", 0.56, "Address info", DocumentTaskStatus.PENDING, null);

            when(embeddingService.embedQuery(query)).thenReturn(vector);
            when(documentService.search(eq(vector), any(), any()))
                .thenReturn(List.of(match));

            DocumentSearchResponse response = searchService.findDocument(Optional.of(clientId), query);

            assertThat(response.documents()).hasSize(1);
            assertThat(response.documents().get(0).title()).isEqualTo("Utility Bill");
        }

        @Test
        void shouldFilterOutPhoneticNoiseUsingReRanking() {
            String query = "utilise billy";
            float[] vector = new float[]{0.11f, 0.21f};
            DocumentSearchResultItem noise = new DocumentSearchResultItem(UUID.randomUUID(), UUID.randomUUID(), "Utility Bill", 0.16, "Content", DocumentTaskStatus.PENDING, null);

            when(embeddingService.embedQuery(query)).thenReturn(vector);
            when(documentService.search(eq(vector), any(), any()))
                .thenReturn(List.of(noise));

            DocumentSearchResponse response = searchService.findDocument(Optional.empty(), query);

            assertThat(response.documents()).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void shouldThrowExceptionWhenDocumentQueryIsInvalid(String invalidQuery) {
            assertThatThrownBy(() -> searchService.findDocument(Optional.empty(), invalidQuery))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldPropagateClientIdToRepository() {
            String query = "tax report";
            when(embeddingService.embedQuery(query)).thenReturn(new float[]{0.5f});

            searchService.findDocument(Optional.of(clientId), query);

            verify(documentService).search(any(), any(), eq(Optional.of(clientId)));
        }

        @Test
        void shouldHandleGlobalSearchWhenClientIdIsMissing() {
            String query = "general terms";
            when(embeddingService.embedQuery(query)).thenReturn(new float[]{0.5f});

            searchService.findDocument(Optional.empty(), query);

            verify(documentService).search(any(), any(), eq(Optional.empty()));
        }
    }
}