package com.genaicbi.model;

public record ValidationResult(
        boolean valid,
        String sanitizedSql,
        String reason
) {
}
