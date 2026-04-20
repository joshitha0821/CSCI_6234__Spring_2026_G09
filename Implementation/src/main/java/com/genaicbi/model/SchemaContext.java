package com.genaicbi.model;

import java.util.List;
import java.util.Set;

public record SchemaContext(
        String connectionId,
        String databaseProductName,
        List<DatabaseTableInfo> tables,
        Set<String> allowedTableNames
) {

    public String toPromptText(int maxTables, int maxColumnsPerTable) {
        StringBuilder sb = new StringBuilder();
        sb.append("Database product: ").append(databaseProductName).append("\n");
        sb.append("Connection ID: ").append(connectionId).append("\n");
        sb.append("Available tables/views:\n");

        int tableCount = 0;
        for (DatabaseTableInfo table : tables) {
            if (tableCount >= maxTables) {
                break;
            }
            tableCount++;
            sb.append("- ").append(table.displayName())
                    .append(" [").append(table.tableType()).append("]\n");
            int colCount = 0;
            for (DatabaseColumnInfo column : table.columns()) {
                if (colCount >= maxColumnsPerTable) {
                    break;
                }
                colCount++;
                sb.append("  - ")
                        .append(column.name())
                        .append(" : ")
                        .append(column.typeName())
                        .append(column.nullable() ? " (nullable)" : " (not null)")
                        .append("\n");
            }
        }

        if (tableCount == 0) {
            sb.append("- No user tables discovered.\n");
        }
        return sb.toString();
    }
}
