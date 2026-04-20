package com.genaicbi.model;

public record DatabaseColumnInfo(
        String name,
        String typeName,
        boolean nullable
) {
}
