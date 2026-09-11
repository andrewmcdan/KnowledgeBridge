package com.capstone.knowledgebridge.common;

import java.time.Instant;

public record HealthResponse(String status, String database, Instant timestamp) {
}
