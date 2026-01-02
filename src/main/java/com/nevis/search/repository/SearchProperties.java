package com.nevis.search.repository;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Validated
@ConfigurationProperties(prefix = "app.search")
public record SearchProperties(
	@NotNull @Min(0) @Max(1) Double threshold,
	@NotNull @Min(1) @Max(100) Integer limit
) {}