package com.genaicbi.model;

import java.time.Instant;

public record ChatHistoryEntry(
        String id,
        String userId,
        String question,
        String connectionId,
        String cacheKey,
        String source,
        boolean fromCache,
        int rowCount,
        double confidenceScore,
        long latencyMs,
        String summary,
        Instant createdAt
) {
}
