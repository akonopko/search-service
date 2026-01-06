package com.nevis.search.controller;

public record ErrorResponse(
    String message,
    int errorCode,
    long timestamp
) {}