package com.nevis.search.event;

import java.util.UUID;

public record DocumentIngestedEvent(UUID documentId) {}