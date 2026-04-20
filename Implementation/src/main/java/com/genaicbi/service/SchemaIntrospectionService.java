package com.genaicbi.service;

import com.genaicbi.model.DatabaseColumnInfo;
import com.genaicbi.model.DatabaseTableInfo;
import com.genaicbi.model.SchemaContext;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SchemaIntrospectionService {

    private static final Set<String> IGNORED_SCHEMAS = Set.of(
            "information_schema",
            "pg_catalog",
            "sys",
            "mysql",
            "performance_schema"
    );

    private final DatabaseConnectionService connectionService;

    public SchemaIntrospectionService(DatabaseConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    public SchemaContext introspect(String requestedConnectionId) {
        String resolvedConnectionId = connectionService.resolveConnectionId(requestedConnectionId);
        DataSource dataSource = connectionService.dataSource(resolvedConnectionId);

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            String databaseProduct = meta.getDatabaseProductName();

            List<DatabaseTableInfo> tables = loadTables(meta, connection.getCatalog());
            tables.sort(Comparator.comparing(DatabaseTableInfo::displayName, String.CASE_INSENSITIVE_ORDER));

            Set<String> allowed = new HashSet<>();
            for (DatabaseTableInfo table : tables) {
                String fullyQualified = table.displayName().toLowerCase(Locale.ROOT);
                String shortName = table.tableName().toLowerCase(Locale.ROOT);
                allowed.add(fullyQualified);
                allowed.add(shortName);
            }

            return new SchemaContext(
                    resolvedConnectionId,
                    databaseProduct == null ? "UnknownDB" : databaseProduct,
                    tables,
                    allowed
            );
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to introspect schema: " + ex.getMessage(), ex);
        }
    }

    private List<DatabaseTableInfo> loadTables(DatabaseMetaData meta, String catalog) throws SQLException {
        List<DatabaseTableInfo> tables = new ArrayList<>();
        try (ResultSet rs = meta.getTables(catalog, null, "%", new String[]{"TABLE", "VIEW"})) {
            while (rs.next()) {
                String schema = rs.getString("TABLE_SCHEM");
                String tableName = rs.getString("TABLE_NAME");
                String tableType = rs.getString("TABLE_TYPE");

                if (tableName == null || tableName.isBlank()) {
                    continue;
                }
                if (schema != null && IGNORED_SCHEMAS.contains(schema.toLowerCase(Locale.ROOT))) {
                    continue;
                }

                List<DatabaseColumnInfo> columns = loadColumns(meta, catalog, schema, tableName);
                tables.add(new DatabaseTableInfo(schema, tableName, tableType, columns));
            }
        }
        return tables;
    }

    private List<DatabaseColumnInfo> loadColumns(DatabaseMetaData meta, String catalog, String schema, String tableName) throws SQLException {
        List<DatabaseColumnInfo> columns = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(catalog, schema, tableName, "%")) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                String type = rs.getString("TYPE_NAME");
                int nullable = rs.getInt("NULLABLE");
                columns.add(new DatabaseColumnInfo(name, type, nullable != DatabaseMetaData.columnNoNulls));
            }
        }
        return columns;
    }
}
