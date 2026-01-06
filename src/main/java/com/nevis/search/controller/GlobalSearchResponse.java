package com.nevis.search.controller;

import java.util.List;

public record GlobalSearchResponse(
    List<ClientSearchResultItem> clientMatches,
    List<ClientSearchResultItem> clientSuggestions,
    List<DocumentSearchResultItem> documents
) {}