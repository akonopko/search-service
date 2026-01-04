package com.nevis.search.model;

import java.util.List;

public record DocumentSearchResponse(
    List<Document> documents
) {}