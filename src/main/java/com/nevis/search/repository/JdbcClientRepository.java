package com.nevis.search.repository;

import com.nevis.search.model.Client;
import com.nevis.search.model.SearchResponse;
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

    @Override
    @Transactional(readOnly = true)
    public SearchResponse search(String query) {
        return null;
    }

}