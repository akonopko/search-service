package com.nevis.search.controller;

import java.util.List;

public record ClientSearchResponse(
	List<ClientSearchResultItem> matches,
	List<ClientSearchResultItem> suggestions
) {}