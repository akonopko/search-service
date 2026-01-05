package com.nevis.search.controller;

public record ErrorResponse(
    String message,
    String errorCode,
    long timestamp
) {}