package com.genaicbi.model;

public record DatabaseConnectionInfo(
        String id,
        String name,
        String jdbcUrl,
        String username,
        String driverClassName,
        boolean active,
        boolean local
) {
}
