package com.genaicbi.service;

import org.springframework.stereotype.Service;

@Service
public class GenAiSqlGeneratorService {

    private final TrainingDataService trainingDataService;

    public GenAiSqlGeneratorService(TrainingDataService trainingDataService) {
        this.trainingDataService = trainingDataService;
    }

    public String generateSql(String question) {
        return trainingDataService.findSqlForQuestion(question)
                .orElseGet(() -> heuristicSql(question));
    }

    private String heuristicSql(String question) {
        String normalized = question.toLowerCase();

        if (normalized.contains("last month") && normalized.contains("sales") && normalized.contains("region")) {
            return """
                    SELECT region, SUM(amount) AS total_sales
                    FROM sales
                    WHERE order_date >= DATEADD('MONTH', -1, CURRENT_DATE())
                    GROUP BY region
                    ORDER BY total_sales DESC
                    LIMIT 20
                    """;
        }

        if (normalized.contains("sales") && normalized.contains("region")) {
            return """
                    SELECT region, SUM(amount) AS total_sales
                    FROM sales
                    GROUP BY region
                    ORDER BY total_sales DESC
                    LIMIT 20
                    """;
        }

        if (normalized.contains("top") && normalized.contains("customer")) {
            return """
                    SELECT customer, SUM(amount) AS total_sales
                    FROM sales
                    GROUP BY customer
                    ORDER BY total_sales DESC
                    LIMIT 10
                    """;
        }

        if (normalized.contains("daily") && normalized.contains("sales")) {
            return """
                    SELECT order_date, SUM(amount) AS total_sales
                    FROM sales
                    GROUP BY order_date
                    ORDER BY order_date
                    LIMIT 60
                    """;
        }

        if (normalized.contains("inventory") || normalized.contains("stock")) {
            return """
                    SELECT product, stock, reorder_level
                    FROM inventory
                    ORDER BY stock ASC
                    LIMIT 50
                    """;
        }

        if (normalized.contains("average") && normalized.contains("order")) {
            return """
                    SELECT region, AVG(amount) AS avg_order_value
                    FROM sales
                    GROUP BY region
                    ORDER BY avg_order_value DESC
                    LIMIT 20
                    """;
        }

        return """
                SELECT order_date, region, customer, product, amount, quantity
                FROM sales
                ORDER BY order_date DESC
                LIMIT 20
                """;
    }
}
