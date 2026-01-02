package com.nevis.search.model;

import java.util.List;

public record SearchResponse(
	List<Client> matches,      // Точные или очень близкие совпадения
	List<Client> suggestions   // "Возможно, вы имели в виду"
) {}