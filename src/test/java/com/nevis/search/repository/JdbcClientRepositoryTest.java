package com.nevis.search.repository;

import com.nevis.search.model.Client;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcClientRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private ClientRepository repository;

    @Test
    @DisplayName("Happy Path: Save and find client")
    void shouldSaveAndFindClient() {
        String firstName = "Alexander";
        Client client = new Client(
            null,
            firstName,
            "Konopko",
            "alex@example.com",
            "Developer",
            List.of("github.com/akonopko"),
            null,
            null
        );

        Client saved = repository.save(client);

        assertThat(saved.id()).isNotNull();
        assertThat(saved.firstName()).isEqualTo(firstName);
        assertThat(repository.findById(saved.id())).isPresent();
    }
    
    @Test
    @DisplayName("Edge Case: Duplicate email should fail")
    void shouldFailOnDuplicateEmail() {
        String email = "unique@test.com";
        repository.save(new Client(null, "U1", "T", email, null, List.of(), null, null));
        Client dupe = new Client(null, "U2", "T", email, null, List.of(), null, null);

        assertThrows(DataIntegrityViolationException.class, () -> repository.save(dupe));
    }

    @Test
    @DisplayName("Constraint: Uppercase email should fail (CHECK constraint)")
    void shouldFailOnUppercaseEmail() {
        Client invalid = new Client(null, "Alex", "K", "UPPER@test.com", null, List.of(), null, null);
        assertThrows(DataIntegrityViolationException.class, () -> repository.save(invalid));
    }

    @Test
    @DisplayName("Mapping: Handle empty social links array")
    void shouldHandleEmptyArrays() {
        Client client = new Client(null, "Alex", "K", "empty@test.com", null, List.of(), null, null);
        Client saved = repository.save(client);
        assertThat(saved.socialLinks()).isEmpty();
    }
}