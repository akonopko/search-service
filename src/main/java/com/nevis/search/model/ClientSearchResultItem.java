package com.nevis.search.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ClientSearchResultItem(
    UUID id,
    @JsonProperty("client_id") UUID clientId,
    @JsonProperty("first_name") String firstName,
    @JsonProperty("last_name") String lastName,
    String email,
    String description,
    double score,
    @JsonProperty("created_at") OffsetDateTime createdAt
) {
    public static ClientSearchResultItem from(Client client, double score) {
        return new ClientSearchResultItem(
            client.id(),
            client.id(),
            client.firstName(),
            client.lastName(),
            client.email(),
            client.description(),
            score,
            client.createdAt()
        );
    }
}