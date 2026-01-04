package com.nevis.search.service;

import com.nevis.search.model.Client;
import com.nevis.search.model.ClientSearchResponse;
import com.nevis.search.model.DocumentSearchResponse;
import com.nevis.search.repository.ClientRepository;
import com.nevis.search.repository.DocumentChunkRepository;
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
    private ClientRepository clientRepository;

    @Mock
    private DocumentChunkRepository chunkRepository;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private ReRankingService reRankingService;

    @InjectMocks
    private SearchServiceImpl searchService;

    @Nested
    @DisplayName("findClient tests")
    class ClientSearchTests {

        @Test
        void shouldReturnClientsWhenQueryIsValid() {
            String query = "John Doe";
            Client mockClient = mock(Client.class);
            ClientSearchResponse johnDoe = new ClientSearchResponse(List.of(mockClient), Collections.emptyList());
            when(clientRepository.search(query))
                .thenReturn(johnDoe);

            ClientSearchResponse response = searchService.findClient(query);

            assertThat(response.matches()).isNotEmpty();
            verify(clientRepository).search(query);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "a", "ab"})
        void shouldThrowExceptionWhenQueryIsTooShort(String invalidQuery) {
            assertThatThrownBy(() -> searchService.findClient(invalidQuery))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldThrowExceptionWhenQueryExceedsMaxLength() {
            String longQuery = "a".repeat(501);
            assertThatThrownBy(() -> searchService.findClient(longQuery))
                .isInstanceOf(IllegalArgumentException.class);
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
            DocumentSearchResult match = new DocumentSearchResult(UUID.randomUUID(), "Utility Bill", "Address info", 0.56);

            when(embeddingService.embedQuery(query)).thenReturn(vector);
            when(chunkRepository.findSimilar(eq(vector), anyInt(), any()))
                .thenReturn(List.of(match));

            when(reRankingService.reRank(query, List.of(match)))
                .thenReturn(List.of(match));

            DocumentSearchResponse response = searchService.findDocument(Optional.of(clientId), query);

            assertThat(response.documents()).hasSize(1);
            assertThat(response.documents().get(0).title()).isEqualTo("Utility Bill");
        }

        @Test
        void shouldFilterOutPhoneticNoiseUsingReRanking() {
            String query = "utilise billy";
            float[] vector = new float[]{0.11f, 0.21f};
            DocumentSearchResult noise = new DocumentSearchResult(UUID.randomUUID(), "Utility Bill", "Content", 0.51);

            when(embeddingService.embedQuery(query)).thenReturn(vector);
            when(chunkRepository.findSimilar(eq(vector), anyInt(), any()))
                .thenReturn(List.of(noise));
            when(reRankingService.reRank(query, List.of(noise)))
                .thenReturn(List.of());

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

            verify(chunkRepository).findSimilar(any(), anyInt(), eq(Optional.of(clientId)));
        }

        @Test
        void shouldHandleGlobalSearchWhenClientIdIsMissing() {
            String query = "general terms";
            when(embeddingService.embedQuery(query)).thenReturn(new float[]{0.5f});

            searchService.findDocument(Optional.empty(), query);

            verify(chunkRepository).findSimilar(any(), anyInt(), eq(Optional.empty()));
        }
    }
}