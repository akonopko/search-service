package com.nevis.search.repository;

import com.nevis.search.model.Client;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class JdbcClientRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private ClientRepository repository;

    @Test
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
}