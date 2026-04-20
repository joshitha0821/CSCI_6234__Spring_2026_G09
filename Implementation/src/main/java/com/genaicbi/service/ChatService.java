package com.genaicbi.service;

import com.genaicbi.dto.AskQuestionResponse;
import com.genaicbi.exception.ResourceNotFoundException;
import com.genaicbi.exception.UnsafeSqlException;
import com.genaicbi.model.CacheEntry;
import com.genaicbi.model.ChatHistoryEntry;
import com.genaicbi.model.QueryResult;
import com.genaicbi.model.ReportResult;
import com.genaicbi.model.SchemaContext;
import com.genaicbi.model.ValidationResult;
import com.genaicbi.util.TextUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class ChatService {

    private final CacheService cacheService;
    private final VectorSearchService vectorSearchService;
    private final GenAiSqlGeneratorService sqlGeneratorService;
    private final SqlValidatorService sqlValidatorService;
    private final QueryExecutorService queryExecutorService;
    private final ReportService reportService;
    private final AuditService auditService;
    private final TrainingDataService trainingDataService;
    private final ConversationService conversationService;
    private final DatabaseConnectionService connectionService;
    private final SchemaIntrospectionService schemaIntrospectionService;
    private final double similarityThreshold;
    private static final Set<String> COMMON_SUGGESTIONS = Set.of(
            "What were last month sales by region?",
            "Show top customers by sales",
            "Show daily sales trend",
            "Which products have the lowest stock?"
    );

    public ChatService(
            CacheService cacheService,
            VectorSearchService vectorSearchService,
            GenAiSqlGeneratorService sqlGeneratorService,
            SqlValidatorService sqlValidatorService,
            QueryExecutorService queryExecutorService,
            ReportService reportService,
            AuditService auditService,
            TrainingDataService trainingDataService,
            ConversationService conversationService,
            DatabaseConnectionService connectionService,
            SchemaIntrospectionService schemaIntrospectionService,
            @Value("${app.vector.similarity-threshold:0.72}") double similarityThreshold
    ) {
        this.cacheService = cacheService;
        this.vectorSearchService = vectorSearchService;
        this.sqlGeneratorService = sqlGeneratorService;
        this.sqlValidatorService = sqlValidatorService;
        this.queryExecutorService = queryExecutorService;
        this.reportService = reportService;
        this.auditService = auditService;
        this.trainingDataService = trainingDataService;
        this.conversationService = conversationService;
        this.connectionService = connectionService;
        this.schemaIntrospectionService = schemaIntrospectionService;
        this.similarityThreshold = similarityThreshold;
    }

    public AskQuestionResponse ask(String userId, String question, String connectionId) {
        long start = System.currentTimeMillis();
        String resolvedConnectionId = connectionService.resolveConnectionId(connectionId);
        String normalized = TextUtil.normalize(question);
        String key = TextUtil.sha256(resolvedConnectionId + "::" + normalized);
        List<String> trail = new ArrayList<>();
        trail.add("Using connection '" + resolvedConnectionId + "' and normalized question hash.");

        Optional<CacheEntry> direct = cacheService.getDirect(key);
        if (direct.isPresent()) {
            trail.add("Found direct cache entry in O(1) lookup for this connection.");
            auditService.log(userId, "ask_question", "cache_hit", elapsed(start), "Direct cache hit");
            AskQuestionResponse response = toResponse(
                    direct.get(),
                    question,
                    true,
                    false,
                    "DIRECT_CACHE",
                    elapsed(start),
                    trail
            );
            conversationService.append(userId, question, resolvedConnectionId, response);
            return response;
        }
        trail.add("No direct cache hit. Running semantic search within connection namespace.");

        Optional<String> similarKey = vectorSearchService.search(resolvedConnectionId, normalized, similarityThreshold);
        if (similarKey.isPresent()) {
            Optional<CacheEntry> semanticEntry = cacheService.getByKey(similarKey.get(), true);
            if (semanticEntry.isPresent()) {
                trail.add("Found semantic cache hit and reused validated artifacts.");
                CacheEntry sourceEntry = semanticEntry.get();
                CacheEntry aliased = new CacheEntry(
                        key,
                        normalized,
                        sourceEntry.getSql(),
                        sourceEntry.getColumns(),
                        sourceEntry.getRows(),
                        sourceEntry.getChart(),
                        sourceEntry.getSummary(),
                        Instant.now(),
                        cacheService.defaultExpiryFromNow()
                );
                cacheService.put(aliased);
                vectorSearchService.upsert(resolvedConnectionId, normalized, key);
                auditService.log(userId, "ask_question", "vector_hit", elapsed(start), "Semantic cache hit");
                AskQuestionResponse response = toResponse(
                        aliased,
                        question,
                        true,
                        true,
                        "VECTOR_CACHE",
                        elapsed(start),
                        trail
                );
                conversationService.append(userId, question, resolvedConnectionId, response);
                return response;
            }
        }
        trail.add("No semantic cache hit. Building schema context for Gemini SQL generation.");

        SchemaContext schemaContext = schemaIntrospectionService.introspect(resolvedConnectionId);
        trail.add("Schema introspected: " + schemaContext.tables().size() + " table/view sources discovered.");

        String generatedSql = sqlGeneratorService.generateSql(question, schemaContext);
        trail.add("Gemini generated SQL from question and schema context.");

        ValidationResult validation = sqlValidatorService.validate(generatedSql, schemaContext);
        if (!validation.valid()) {
            auditService.log(
                    userId,
                    "ask_question",
                    "genai_sql_rejected",
                    elapsed(start),
                    validation.reason()
            );
            throw new UnsafeSqlException(validation.reason());
        }
        trail.add("SQL validated against read-only policy and schema source whitelist.");

        QueryResult queryResult = queryExecutorService.execute(validation.sanitizedSql(), resolvedConnectionId);
        trail.add("Validated SQL executed successfully on selected connection.");
        ReportResult reportResult = reportService.buildReport(question, queryResult);
        trail.add("Generated chart payload and textual insight summary.");

        CacheEntry cacheEntry = new CacheEntry(
                key,
                normalized,
                validation.sanitizedSql(),
                queryResult.columns(),
                queryResult.rows(),
                reportResult.chart(),
                reportResult.summary(),
                Instant.now(),
                cacheService.defaultExpiryFromNow()
        );
        cacheService.put(cacheEntry);
        vectorSearchService.upsert(resolvedConnectionId, normalized, key);
        trail.add("Stored answer in direct cache and semantic index for future reuse.");

        auditService.log(userId, "ask_question", "genai_success", elapsed(start), "Gemini SQL generated and executed");
        AskQuestionResponse response = toResponse(
                cacheEntry,
                question,
                false,
                false,
                "GEMINI",
                elapsed(start),
                trail
        );
        conversationService.append(userId, question, resolvedConnectionId, response);
        return response;
    }

    public List<ChatHistoryEntry> history(String userId, int limit) {
        return conversationService.listForUser(userId, limit);
    }

    public ConversationService.ConversationStats stats(String userId) {
        return conversationService.statsForUser(userId);
    }

    public List<String> suggestionQuestions(int limit) {
        int safeLimit = Math.max(1, limit);
        List<String> suggestions = new ArrayList<>(COMMON_SUGGESTIONS);
        suggestions.addAll(trainingDataService.listExampleQuestions(safeLimit));
        return suggestions.stream()
                .distinct()
                .limit(safeLimit)
                .toList();
    }

    public String exportCsv(String cacheKey) {
        CacheEntry entry = cacheService.getByKey(cacheKey, false)
                .orElseThrow(() -> new ResourceNotFoundException("No cache entry found for key: " + cacheKey));

        StringBuilder csv = new StringBuilder();
        List<String> columns = entry.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            csv.append(escapeCsv(columns.get(i)));
            if (i < columns.size() - 1) {
                csv.append(",");
            }
        }
        csv.append("\n");

        for (var row : entry.getRows()) {
            for (int i = 0; i < columns.size(); i++) {
                Object value = row.get(columns.get(i));
                csv.append(escapeCsv(value == null ? "" : String.valueOf(value)));
                if (i < columns.size() - 1) {
                    csv.append(",");
                }
            }
            csv.append("\n");
        }
        return csv.toString();
    }

    private AskQuestionResponse toResponse(
            CacheEntry entry,
            String question,
            boolean fromCache,
            boolean semanticMatch,
            String source,
            long latencyMs,
            List<String> trail
    ) {
        List<String> followUps = buildFollowUpSuggestions(question, entry.getColumns());
        double confidence = confidenceFor(entry, source, entry.getRows().size());
        return new AskQuestionResponse(
                entry.getKey(),
                fromCache,
                semanticMatch,
                source,
                entry.getSql(),
                entry.getSummary(),
                entry.getChart(),
                entry.getColumns(),
                entry.getRows(),
                entry.getRows().size(),
                confidence,
                latencyMs,
                List.copyOf(trail),
                followUps
        );
    }

    private List<String> buildFollowUpSuggestions(String question, List<String> columns) {
        String normalized = TextUtil.normalize(question);
        List<String> suggestions = new ArrayList<>();

        if (normalized.contains("sales")) {
            suggestions.add("Compare this result month-over-month.");
            suggestions.add("Break this down by product.");
        }
        if (normalized.contains("region") || containsColumn(columns, "region")) {
            suggestions.add("Which region changed the most versus previous period?");
        }
        if (containsColumn(columns, "order_date") || containsColumn(columns, "date") || normalized.contains("trend")) {
            suggestions.add("Show a weekly trend for the same metric.");
        }
        if (containsColumn(columns, "product")) {
            suggestions.add("Which products are driving the highest revenue?");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("Show the same metric split by category.");
            suggestions.add("Show top contributors for this metric.");
        }
        return suggestions.stream().distinct().limit(3).toList();
    }

    private boolean containsColumn(List<String> columns, String expected) {
        return columns.stream().anyMatch(col -> col.toLowerCase(Locale.ROOT).contains(expected));
    }

    private double confidenceFor(CacheEntry entry, String source, int rowCount) {
        double base;
        if ("DIRECT_CACHE".equals(source)) {
            base = 0.9 + (entry.getHits() * 0.02) + (entry.getTrustScore() * 0.01);
        } else if ("VECTOR_CACHE".equals(source)) {
            base = 0.82 + (entry.getTrustScore() * 0.01);
        } else {
            base = 0.74 + (Math.min(60, rowCount) / 600.0) + (entry.getTrustScore() * 0.005);
        }
        return clamp(round2(base), 0.1, 0.99);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String escapeCsv(String raw) {
        String safe = raw.replace("\"", "\"\"");
        return "\"" + safe + "\"";
    }

    private long elapsed(long start) {
        return Math.max(0L, System.currentTimeMillis() - start);
    }
}
