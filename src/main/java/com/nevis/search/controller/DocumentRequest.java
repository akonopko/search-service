package com.nevis.search.controller;

import jakarta.validation.constraints.NotBlank;

public record DocumentRequest(
    @NotBlank 
    String title,
    
    @NotBlank
    String content
) {}