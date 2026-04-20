package com.genaicbi.model;

import java.util.List;

public record DatabaseTableInfo(
        String schemaName,
        String tableName,
        String tableType,
        List<DatabaseColumnInfo> columns
) {
    public String displayName() {
        if (schemaName == null || schemaName.isBlank()) {
            return tableName;
        }
        return schemaName + "." + tableName;
    }
}
