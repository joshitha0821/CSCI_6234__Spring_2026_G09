package com.genaicbi.service;

import com.genaicbi.model.ValidationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SqlValidatorService {

    private static final Pattern FORBIDDEN_PATTERN = Pattern.compile(
            "\\b(insert|update|delete|drop|truncate|alter|create|grant|revoke|merge|call)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TABLE_PATTERN = Pattern.compile("\\b(from|join)\\s+([a-zA-Z0-9_\\.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIMIT_PATTERN = Pattern.compile("\\blimit\\b", Pattern.CASE_INSENSITIVE);

    private final int maxRows;
    private final Set<String> allowedTables = Set.of("sales", "inventory");

    public SqlValidatorService(@Value("${app.query.max-rows:200}") int maxRows) {
        this.maxRows = maxRows;
    }

    public ValidationResult validate(String sql) {
        if (sql == null || sql.isBlank()) {
            return new ValidationResult(false, null, "Empty SQL");
        }

        String sanitized = sql.strip().replaceAll(";+$", "");
        String lower = sanitized.toLowerCase(Locale.ROOT).trim();

        if (!(lower.startsWith("select") || lower.startsWith("with"))) {
            return new ValidationResult(false, null, "Only SELECT/CTE queries are allowed");
        }
        if (FORBIDDEN_PATTERN.matcher(lower).find()) {
            return new ValidationResult(false, null, "Unsafe SQL keyword detected");
        }

        Matcher tableMatcher = TABLE_PATTERN.matcher(lower);
        boolean hasReferencedTable = false;
        while (tableMatcher.find()) {
            hasReferencedTable = true;
            String table = tableMatcher.group(2);
            String clean = table.contains(".") ? table.substring(table.lastIndexOf('.') + 1) : table;
            if (!allowedTables.contains(clean)) {
                return new ValidationResult(false, null, "Table not allowed: " + clean);
            }
        }
        if (!hasReferencedTable) {
            return new ValidationResult(false, null, "No table reference found");
        }

        if (!LIMIT_PATTERN.matcher(lower).find()) {
            sanitized = sanitized + " LIMIT " + maxRows;
        }
        return new ValidationResult(true, sanitized, null);
    }
}
