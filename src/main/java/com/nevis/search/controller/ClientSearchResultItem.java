package com.nevis.search.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nevis.search.model.Client;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ClientSearchResultItem(
    @JsonProperty("client_id") UUID clientId,
    @JsonProperty("first_name") String firstName,
    @JsonProperty("last_name") String lastName,
    String email,
    String description,
    double score,
    @JsonProperty("social_links") List<String> socialLinks,
    @JsonProperty("created_at") OffsetDateTime createdAt
) {
    public static ClientSearchResultItem from(Client client, double score) {
        return new ClientSearchResultItem(
            client.id(),
            client.firstName(),
            client.lastName(),
            client.email(),
            client.description(),
            score,
            client.socialLinks(),
            client.createdAt()
        );
    }
}