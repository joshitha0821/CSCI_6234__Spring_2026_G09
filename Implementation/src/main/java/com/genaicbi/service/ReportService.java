package com.genaicbi.service;

import com.genaicbi.dto.ChartPayload;
import com.genaicbi.model.QueryResult;
import com.genaicbi.model.ReportResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {

    public ReportResult buildReport(String question, QueryResult queryResult) {
        ChartPayload chart = buildChart(question, queryResult);
        String summary = buildSummary(queryResult);
        return new ReportResult(chart, summary);
    }

    private ChartPayload buildChart(String question, QueryResult result) {
        List<Map<String, Object>> rows = result.rows();
        if (rows.isEmpty()) {
            return new ChartPayload("none", "No chart data", List.of(), List.of());
        }

        List<String> columns = result.columns();
        String labelColumn = null;
        String valueColumn = null;

        for (String col : columns) {
            Object value = rows.get(0).get(col);
            if (labelColumn == null && value instanceof String) {
                labelColumn = col;
            }
            if (valueColumn == null && value instanceof Number) {
                valueColumn = col;
            }
        }

        if (labelColumn == null && !columns.isEmpty()) {
            labelColumn = columns.get(0);
        }
        if (valueColumn == null) {
            return new ChartPayload("table", "Tabular result", List.of(), List.of());
        }

        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (Map<String, Object> row : rows.stream().limit(20).toList()) {
            Object labelValue = row.get(labelColumn);
            Object numericValue = row.get(valueColumn);
            labels.add(labelValue == null ? "(null)" : String.valueOf(labelValue));
            if (numericValue instanceof Number n) {
                values.add(n.doubleValue());
            } else {
                values.add(0.0);
            }
        }

        String type = looksTemporal(labels) ? "line" : "bar";
        return new ChartPayload(type, "Auto chart for: " + question, labels, values);
    }

    private String buildSummary(QueryResult result) {
        int rowCount = result.rows().size();
        if (rowCount == 0) {
            return "No rows returned for this question.";
        }

        String firstNumericCol = null;
        for (String col : result.columns()) {
            Object value = result.rows().get(0).get(col);
            if (value instanceof Number) {
                firstNumericCol = col;
                break;
            }
        }

        if (firstNumericCol == null) {
            return "Returned " + rowCount + " rows. Dataset contains categorical or text fields only.";
        }

        double total = 0.0;
        double max = Double.NEGATIVE_INFINITY;
        double min = Double.POSITIVE_INFINITY;
        String topColumnName = null;
        for (Map<String, Object> row : result.rows()) {
            Object value = row.get(firstNumericCol);
            if (value instanceof Number n) {
                double d = n.doubleValue();
                total += d;
                if (d > max) {
                    max = d;
                    topColumnName = firstLabelValue(row, result.columns(), firstNumericCol);
                }
                min = Math.min(min, d);
            }
        }
        double avg = total / Math.max(1, rowCount);
        String topNote = topColumnName == null ? "" : " Top segment: " + topColumnName + ".";
        return "Returned " + rowCount + " rows. " +
                "For column '" + firstNumericCol + "': total=" + round(total) +
                ", avg=" + round(avg) +
                ", min=" + round(min) +
                ", max=" + round(max) + "." + topNote;
    }

    private String round(double value) {
        return String.format("%.2f", value);
    }

    private String firstLabelValue(Map<String, Object> row, List<String> columns, String numericColumn) {
        for (String column : columns) {
            if (column.equals(numericColumn)) {
                continue;
            }
            Object value = row.get(column);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private boolean looksTemporal(List<String> labels) {
        if (labels.isEmpty()) {
            return false;
        }
        long temporalish = labels.stream()
                .filter(label -> label.matches("\\d{4}-\\d{2}-\\d{2}.*") || label.matches("\\d{2}/\\d{2}/\\d{4}.*"))
                .count();
        return temporalish >= Math.max(1, labels.size() / 2);
    }
}
