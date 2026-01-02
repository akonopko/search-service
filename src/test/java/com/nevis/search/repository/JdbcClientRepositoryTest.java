package com.nevis.search.repository;

import com.nevis.search.model.Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
	"app.search.threshold=0.4",
	"app.search.limit=20"
})
class JdbcClientRepositoryTest extends BaseIntegrationTest {

	@Autowired
	private ClientRepository repository;

	@Autowired
	private JdbcClient jdbcClient;

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

	@Nested
	@DisplayName("Client search test")
	class ClientSearchTest {

		@BeforeEach
		void setUp() {
			jdbcClient.sql("DELETE FROM clients").update();
			saveClient("Aleksandr", "Konopko", "dev@example.com", "Java Developer");
			saveClient("Oleksiy", "Konov", "oleks@test.com", "Python Coder");
			saveClient("Dmitry", "Ivanov", "dima@test.ru", "Frontend");
		}

		@Test
		@DisplayName("1. Full Name: Search by 'First Last' (Exact)")
		void fullNameSearch() {
			var response = repository.search("Aleksandr Konopko");

			assertThat(response.matches()).hasSize(1);
			assertThat(response.matches().get(0).firstName()).isEqualTo("Aleksandr");
		}

		@Test
		@DisplayName("2. Swapped Name: Search by 'Last First' (Exact)")
		void swappedNameSearch() {
			var response = repository.search("Konopko Aleksandr");

			assertThat(response.matches()).hasSize(1);
			assertThat(response.matches().get(0).firstName()).isEqualTo("Aleksandr");
		}

		@Test
		@DisplayName("3. Typo Collision: Check typos in similar names")
		void typoCollisionSearch() {
			var response = repository.search("Aleksanr");

			assertThat(response.matches()).isEmpty();
			assertThat(response.suggestions())
				.extracting(Client::firstName)
				.contains("Aleksandr");
		}

		@Test
		@DisplayName("4. Whitespace: Handle extra spaces and tabs")
		void extraWhitespaces() {
			var response = repository.search("   Aleksandr    Konopko  ");

			assertThat(response.matches().size()).isEqualTo(1);
			assertThat(response.suggestions()).isEmpty();
		}

		@Test
		@DisplayName("5. Substring Overlap: Part of a word inside another")
		void substringOverlap() {
			var response = repository.search("Oleks");

			assertThat(response.matches()).hasSize(1);
			assertThat(response.suggestions()).isEmpty();
			assertThat(response.matches().get(0).firstName()).isEqualTo("Oleksiy");
		}

		@Test
		@DisplayName("6. Short Query: still works")
		void veryShortQuery() {
			var response = repository.search("Ol");

			assertThat(response.matches()).isNotEmpty();
			assertThat(response.matches()).isNotEmpty();
		}

		@Test
		@DisplayName("7. Noisy Description: Search by phrase from the middle of description")
		void middleDescriptionSearch() {
			var response = repository.search("Developer");

			assertThat(response.matches())
				.extracting(Client::description)
				.contains("Java Developer");
		}

		@Test
		@DisplayName("8. Similar characters: Handle character similarity")
		void similarCharsSearch() {
			var response = repository.search("Konovko");

			assertThat(response.matches()).isEmpty();
			assertThat(response.suggestions()).hasSizeGreaterThanOrEqualTo(2);
		}

		@Test
		@DisplayName("9. Null / Empty handling: Boundary conditions")
		void nullEmptyHandling() {
			assertThat(repository.search("")).matches(r -> r.matches().isEmpty());
			assertThat(repository.search(null)).matches(r -> r.matches().isEmpty());
		}

		@Test
		@DisplayName("10. Description Typo: Fuzzy search within description")
		void descriptionTypoSearch() {
			var response = repository.search("Pyton");

			assertThat(response.matches()).isEmpty();
			assertThat(response.suggestions().size()).isEqualTo(1);
			assertThat(response.suggestions())
				.extracting(Client::description)
				.contains("Python Coder");
		}

		@Test
		@DisplayName("11. Cross-Field Search: Match Name and Description combined")
		void crossFieldSearch() {
			var response = repository.search("Dmitry Frontend");

			assertThat(response.matches()).isEmpty();
			assertThat(response.suggestions().size()).isEqualTo(1);
			assertThat(response.suggestions().get(0).lastName()).isEqualTo("Ivanov");
		}

		@Test
		@DisplayName("12. Partial Description: Match start of description word")
		void partialDescriptionSearch() {
			var response = repository.search("Front");

			assertThat(response.matches())
				.extracting(Client::description)
				.contains("Frontend");
		}

		private void saveClient(String firstName, String lastName, String email, String description) {
			jdbcClient.sql("""
					INSERT INTO clients (first_name, last_name, email, description)
					VALUES (:firstName, :lastName, :email, :description)
					""")
				.param("firstName", firstName)
				.param("lastName", lastName)
				.param("email", email)
				.param("description", description)
				.update();
		}
	}
}