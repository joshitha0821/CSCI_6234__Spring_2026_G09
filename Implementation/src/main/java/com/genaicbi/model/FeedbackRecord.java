package com.genaicbi.model;

import java.time.Instant;

public record FeedbackRecord(
        String id,
        String cacheKey,
        VoteType vote,
        String userId,
        String comment,
        Instant createdAt
) {
}
