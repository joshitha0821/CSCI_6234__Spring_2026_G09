package com.genaicbi.model;

import java.time.Instant;

public record AuditEvent(
        String id,
        String userId,
        String action,
        String status,
        long latencyMs,
        String message,
        Instant timestamp
) {
}
