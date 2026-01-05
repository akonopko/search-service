package com.nevis.search.service;

import com.nevis.search.controller.ClientRequest;
import com.nevis.search.controller.ClientResponse;
import com.nevis.search.exception.EntityNotFoundException;
import com.nevis.search.model.Client;
import com.nevis.search.repository.ClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClientServiceTest {

    @Mock
    private ClientRepository clientRepository;

    @InjectMocks
    private ClientServiceImpl clientService;

    private UUID clientId;
    private Client mockClient;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        // Use a real record instance, not a mock(Client.class)
        mockClient = new Client(
            clientId,
            "John",
            "Doe",
            "john.doe@example.com",
            "Description",
            List.of("http://linkedin.com/johndoe"),
            null,
            null
        );
    }

    @Nested
    @DisplayName("Create Client Tests")
    class CreateTests {

        @Test
        @DisplayName("Should successfully create a client and return response")
        void create_ShouldReturnResponse_WhenRequestIsValid() {
            ClientRequest request = new ClientRequest(
                "John", "Doe", "john.doe@example.com", "Description", List.of("http://linkedin.com/johndoe")
            );

            when(clientRepository.save(any(Client.class))).thenReturn(mockClient);

            ClientResponse response = clientService.create(request);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(clientId);
            assertThat(response.firstName()).isEqualTo("John");

            verify(clientRepository).save(any(Client.class));
        }
    }

    @Nested
    @DisplayName("Get Client By ID Tests")
    class GetByIdTests {

        @Test
        void getById_ShouldReturnResponse_WhenClientExists() {
            when(clientRepository.findById(clientId)).thenReturn(Optional.of(mockClient));

            ClientResponse response = clientService.getById(clientId);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(clientId);
            assertThat(response.email()).isEqualTo("john.doe@example.com");
        }

        @Test
        void getById_ShouldThrowException_WhenClientNotFound() {
            when(clientRepository.findById(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> clientService.getById(clientId))
                .isInstanceOf(EntityNotFoundException.class);
        }
    }
}