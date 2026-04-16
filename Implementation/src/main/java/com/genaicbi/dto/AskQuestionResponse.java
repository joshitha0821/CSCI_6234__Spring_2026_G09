package com.genaicbi.dto;

import java.util.List;
import java.util.Map;

public record AskQuestionResponse(
        String cacheKey,
        boolean fromCache,
        boolean semanticMatch,
        String source,
        String sql,
        String summary,
        ChartPayload chart,
        List<String> columns,
        List<Map<String, Object>> rows,
        int rowCount,
        double confidenceScore,
        long latencyMs,
        List<String> reasoningTrail,
        List<String> followUpSuggestions
) {
}
