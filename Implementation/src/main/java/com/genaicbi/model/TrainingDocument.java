package com.genaicbi.model;

import java.time.Instant;

public record TrainingDocument(
        String id,
        String fileName,
        String contentType,
        String content,
        String uploadedBy,
        Instant uploadedAt
) {
}
