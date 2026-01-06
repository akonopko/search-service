package com.nevis.search.repository;

import com.nevis.search.model.Client;
import com.nevis.search.controller.ClientSearchResponse;
import com.nevis.search.controller.ClientSearchResultItem;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcClientRepository implements ClientRepository {

    public static final double DEFAULT_SIMILARITY_THRESHOLD = 0.4;
    public static Integer DEFAULT_LIMIT = 50;

    private record ClientWithScore(Client client, double score, boolean isExact) {}

    private final JdbcClient jdbcClient;

    private final RowMapper<Client> clientRowMapper = (rs, rowNum) -> new Client(
        rs.getObject("id", UUID.class),
        rs.getString("first_name"),
        rs.getString("last_name"),
        rs.getString("email"),
        rs.getString("description"),
        rs.getArray("social_links") == null ? List.of() :
            Arrays.asList((String[]) rs.getArray("social_links").getArray()),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class)
    );

    @Override
    @Transactional
    public Client save(Client client) {
        return jdbcClient.sql("""
                INSERT INTO clients (first_name, last_name, email, description, social_links)
                VALUES (:firstName, :lastName, :email, :description, :socialLinks)
                RETURNING id, first_name, last_name, email, description, social_links, created_at, updated_at
                """)
            .param("firstName", client.firstName())
            .param("lastName", client.lastName())
            .param("email", client.email())
            .param("description", client.description())
            .param("socialLinks", client.socialLinks() == null ? new String[0] : client.socialLinks().toArray(new String[0]))
            .query(clientRowMapper)
            .single();
    }

    @Override
    public Optional<Client> findById(UUID id) {
        return jdbcClient.sql("SELECT * FROM clients WHERE id = :id")
            .param("id", id)
            .query(clientRowMapper)
            .optional();
    }

    @Transactional(readOnly = true)
    @Override
    public ClientSearchResponse search(String query, Optional<Integer> limit, Optional<Double> similarity) {
        if (query == null || query.isBlank()) {
            return new ClientSearchResponse(List.of(), List.of());
        }

        Double resultSimilarity = similarity.orElse(DEFAULT_SIMILARITY_THRESHOLD);

        jdbcClient
            .sql("SET LOCAL pg_trgm.strict_word_similarity_threshold = " + resultSimilarity)
            .update();

        String cleanQuery = query.trim().toLowerCase();

        String sql = """
            SELECT *, 
                   strict_word_similarity(:query, t.full_text) as score,
                   ((t.full_text ILIKE :pattern) OR (strict_word_similarity(:query, t.full_text) >= 1.0)) as is_exact
            FROM (
                SELECT *, 
                       LOWER(concat_ws(' ', first_name, last_name, email, description)) as full_text
                FROM clients
            ) t
            WHERE t.full_text ILIKE :pattern 
               OR :query <<% t.full_text
            ORDER BY is_exact DESC, score DESC, id ASC
            LIMIT :limit
            """;

        Integer resultLimit = limit.orElse(DEFAULT_LIMIT);

        var results = jdbcClient.sql(sql)
            .param("query", cleanQuery)
            .param("pattern", "%" + cleanQuery + "%")
            .param("limit", resultLimit)
            .query((rs, rowNum) -> new ClientWithScore(
                clientRowMapper.mapRow(rs, rowNum),
                rs.getDouble("score"),
                rs.getBoolean("is_exact")
            )).list();

        return new ClientSearchResponse(
            results.stream()
                .filter(ClientWithScore::isExact)
                .map(r -> ClientSearchResultItem.from(r.client(), r.score()))
                .toList(),
            results.stream()
                .filter(r -> !r.isExact())
                .map(r -> ClientSearchResultItem.from(r.client(), r.score()))
                .toList()
        );
    }

}