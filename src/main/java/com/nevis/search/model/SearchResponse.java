package com.nevis.search.model;

import java.util.List;

public record SearchResponse(
	List<Client> matches,
	List<Client> suggestions
) {}