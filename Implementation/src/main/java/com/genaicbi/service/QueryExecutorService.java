package com.genaicbi.service;

import com.genaicbi.model.QueryResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class QueryExecutorService {

    private final DatabaseConnectionService connectionService;

    public QueryExecutorService(DatabaseConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    public QueryResult execute(String sql, String connectionId) {
        JdbcTemplate jdbcTemplate = connectionService.jdbcTemplate(connectionId);
        List<Map<String, Object>> raw = jdbcTemplate.queryForList(sql);
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> columns = new ArrayList<>();

        for (Map<String, Object> row : raw) {
            LinkedHashMap<String, Object> sanitizedRow = new LinkedHashMap<>();
            row.forEach((k, v) -> sanitizedRow.put(k, sanitizeValue(v)));
            rows.add(sanitizedRow);
        }

        if (!rows.isEmpty()) {
            columns.addAll(rows.get(0).keySet());
        }

        return new QueryResult(columns, rows);
    }

    private Object sanitizeValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.doubleValue();
        }
        if (value instanceof Date date) {
            LocalDate localDate = date.toLocalDate();
            return localDate.toString();
        }
        if (value instanceof Timestamp timestamp) {
            LocalDateTime localDateTime = timestamp.toLocalDateTime();
            return localDateTime.toString();
        }
        return value;
    }
}
