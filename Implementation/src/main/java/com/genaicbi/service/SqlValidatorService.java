package com.genaicbi.service;

import com.genaicbi.model.SchemaContext;
import com.genaicbi.model.ValidationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SqlValidatorService {

    private static final Pattern FORBIDDEN_PATTERN = Pattern.compile(
            "\\b(insert|update|delete|drop|truncate|alter|create|grant|revoke|merge|call|execute|exec)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TABLE_PATTERN = Pattern.compile("\\b(from|join)\\s+([a-zA-Z0-9_\\.\"`\\[\\]]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CTE_PATTERN = Pattern.compile("\\bwith\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s+as\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern CTE_CHAIN_PATTERN = Pattern.compile(",\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s+as\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIMIT_PATTERN = Pattern.compile("\\blimit\\s+\\d+\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FETCH_PATTERN = Pattern.compile("\\bfetch\\s+first\\s+\\d+\\s+rows\\s+only\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOP_PATTERN = Pattern.compile("\\bselect\\s+top\\s+\\d+\\b", Pattern.CASE_INSENSITIVE);

    private final int maxRows;

    public SqlValidatorService(@Value("${app.query.max-rows:200}") int maxRows) {
        this.maxRows = maxRows;
    }

    public ValidationResult validate(String sql, SchemaContext schemaContext) {
        if (sql == null || sql.isBlank()) {
            return new ValidationResult(false, null, "Empty SQL");
        }

        String sanitized = sql.strip().replaceAll("^```sql", "").replaceAll("```$", "").strip();
        sanitized = sanitized.replaceAll(";+$", "");
        if (sanitized.contains(";")) {
            return new ValidationResult(false, null, "Multiple SQL statements are not allowed");
        }

        String lower = sanitized.toLowerCase(Locale.ROOT).trim();
        if (!(lower.startsWith("select") || lower.startsWith("with"))) {
            return new ValidationResult(false, null, "Only SELECT/CTE queries are allowed");
        }
        if (FORBIDDEN_PATTERN.matcher(lower).find()) {
            return new ValidationResult(false, null, "Unsafe SQL keyword detected");
        }

        Set<String> allowedTables = new HashSet<>(schemaContext.allowedTableNames());
        Set<String> cteNames = extractCteNames(lower);

        Matcher tableMatcher = TABLE_PATTERN.matcher(lower);
        boolean hasReferencedSource = false;
        while (tableMatcher.find()) {
            String rawRef = normalizeIdentifier(tableMatcher.group(2));
            if (rawRef.isBlank()) {
                continue;
            }
            hasReferencedSource = true;

            String shortName = rawRef.contains(".")
                    ? rawRef.substring(rawRef.lastIndexOf('.') + 1)
                    : rawRef;
            if (cteNames.contains(rawRef) || cteNames.contains(shortName)) {
                continue;
            }
            if (!allowedTables.contains(rawRef) && !allowedTables.contains(shortName)) {
                return new ValidationResult(false, null, "Table or view not allowed: " + rawRef);
            }
        }

        if (!hasReferencedSource) {
            return new ValidationResult(false, null, "No FROM/JOIN source found in SQL");
        }

        if (!LIMIT_PATTERN.matcher(lower).find()
                && !FETCH_PATTERN.matcher(lower).find()
                && !TOP_PATTERN.matcher(lower).find()) {
            sanitized = "SELECT * FROM (" + sanitized + ") AS _safe_query LIMIT " + maxRows;
        }
        return new ValidationResult(true, sanitized, null);
    }

    private Set<String> extractCteNames(String sqlLower) {
        Set<String> cteNames = new HashSet<>();
        Matcher first = CTE_PATTERN.matcher(sqlLower);
        if (first.find()) {
            cteNames.add(first.group(1).toLowerCase(Locale.ROOT));
        }
        Matcher chain = CTE_CHAIN_PATTERN.matcher(sqlLower);
        while (chain.find()) {
            cteNames.add(chain.group(1).toLowerCase(Locale.ROOT));
        }
        return cteNames;
    }

    private String normalizeIdentifier(String rawIdentifier) {
        if (rawIdentifier == null) {
            return "";
        }
        return rawIdentifier
                .replace("\"", "")
                .replace("`", "")
                .replace("[", "")
                .replace("]", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
