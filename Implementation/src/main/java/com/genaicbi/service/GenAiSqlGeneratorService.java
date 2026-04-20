package com.genaicbi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genaicbi.exception.AiProviderException;
import com.genaicbi.model.SchemaContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GenAiSqlGeneratorService {

    private static final Pattern SQL_CODE_BLOCK = Pattern.compile("```sql\\s*(.*?)```", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SELECT_FALLBACK = Pattern.compile("(?is)(select\\s+.+)");

    private final GeminiClientService geminiClientService;
    private final TrainingDataService trainingDataService;
    private final ObjectMapper objectMapper;
    private final int defaultLimit;

    public GenAiSqlGeneratorService(
            GeminiClientService geminiClientService,
            TrainingDataService trainingDataService,
            ObjectMapper objectMapper,
            @Value("${app.query.max-rows:200}") int defaultLimit
    ) {
        this.geminiClientService = geminiClientService;
        this.trainingDataService = trainingDataService;
        this.objectMapper = objectMapper;
        this.defaultLimit = defaultLimit;
    }

    public String generateSql(String question, SchemaContext schemaContext) {
        String prompt = buildPrompt(question, schemaContext);
        String rawResponse = geminiClientService.generateContent(prompt);
        String parsedSql = extractSql(rawResponse);
        if (parsedSql == null || parsedSql.isBlank()) {
            throw new AiProviderException("Gemini response did not include a usable SQL query.");
        }
        return parsedSql.trim();
    }

    private String buildPrompt(String question, SchemaContext schemaContext) {
        List<String> examples = trainingDataService.listExamples().stream().limit(6).toList();
        StringBuilder sb = new StringBuilder();
        sb.append("You are a strict SQL generation engine for business analytics.\n");
        sb.append("Current date: ").append(LocalDate.now()).append("\n");
        sb.append("Task: Generate a single SQL query for the user question.\n\n");

        sb.append("Hard rules:\n");
        sb.append("1) Output only JSON object: {\"sql\":\"...\",\"notes\":\"...\"}\n");
        sb.append("2) SQL must be read-only (SELECT or WITH ... SELECT)\n");
        sb.append("3) Use only tables/columns available in schema context\n");
        sb.append("4) Prefer explicit joins when needed\n");
        sb.append("5) Add ORDER BY for ranking/time trend when meaningful\n");
        sb.append("6) Enforce reasonable row cap with LIMIT ").append(defaultLimit).append(" if user did not specify one\n");
        sb.append("7) If question is ambiguous, choose the most likely business interpretation\n\n");

        sb.append("Schema context:\n");
        sb.append(schemaContext.toPromptText(80, 60)).append("\n");

        if (!examples.isEmpty()) {
            sb.append("Approved example mappings (question => SQL):\n");
            for (String ex : examples) {
                sb.append("- ").append(ex).append("\n");
            }
            sb.append("\n");
        }

        sb.append("User question:\n");
        sb.append(question).append("\n");
        return sb.toString();
    }

    private String extractSql(String rawResponse) {
        String trimmed = rawResponse == null ? "" : rawResponse.trim();
        if (trimmed.isBlank()) {
            return null;
        }

        String fromJson = extractSqlFromJson(trimmed);
        if (fromJson != null && !fromJson.isBlank()) {
            return stripFenceAndSemicolon(fromJson);
        }

        Matcher codeBlock = SQL_CODE_BLOCK.matcher(trimmed);
        if (codeBlock.find()) {
            return stripFenceAndSemicolon(codeBlock.group(1));
        }

        Matcher selectFallback = SELECT_FALLBACK.matcher(trimmed);
        if (selectFallback.find()) {
            return stripFenceAndSemicolon(selectFallback.group(1));
        }
        return null;
    }

    private String extractSqlFromJson(String text) {
        try {
            JsonNode node = objectMapper.readTree(text);
            JsonNode sqlNode = node.path("sql");
            if (!sqlNode.isMissingNode() && !sqlNode.asText().isBlank()) {
                return sqlNode.asText();
            }
        } catch (IOException ignored) {
            // Not JSON, fallback parsers handle this.
        }
        return null;
    }

    private String stripFenceAndSemicolon(String sql) {
        return sql.strip()
                .replaceAll("^```sql", "")
                .replaceAll("```$", "")
                .replaceAll(";+$", "")
                .strip();
    }
}
