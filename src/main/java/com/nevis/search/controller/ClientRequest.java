package com.nevis.search.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ClientRequest(
    @NotBlank @JsonProperty("first_name") String firstName,
    @NotBlank @JsonProperty("last_name") String lastName,
    @Email @NotBlank String email,
    String description,
    @JsonProperty("social_links") List<String> socialLinks
) {}