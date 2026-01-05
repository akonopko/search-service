package com.nevis.search.model;

import java.util.List;

public record ClientSearchResponse(
	List<ClientSearchResultItem> matches,
	List<ClientSearchResultItem> suggestions
) {}