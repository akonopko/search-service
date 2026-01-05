package com.nevis.search.controller;

import java.util.List;

public record DocumentSearchResponse(
    List<DocumentSearchResultItem> documents
) {}