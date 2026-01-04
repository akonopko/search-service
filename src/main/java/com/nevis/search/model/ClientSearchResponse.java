package com.nevis.search.model;

import java.util.List;

public record ClientSearchResponse(
	List<Client> matches,
	List<Client> suggestions
) {}