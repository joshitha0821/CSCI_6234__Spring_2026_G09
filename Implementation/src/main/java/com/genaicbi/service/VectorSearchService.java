package com.genaicbi.service;

import com.genaicbi.util.TextUtil;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VectorSearchService {

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "the", "is", "are", "to", "for", "of", "in", "on", "by", "with", "from",
            "what", "show", "me", "please", "could", "you"
    );

    private final ConcurrentHashMap<String, Map<String, Double>> vectorsByCacheKey = new ConcurrentHashMap<>();

    public void upsert(String text, String cacheKey) {
        vectorsByCacheKey.put(cacheKey, embed(text));
    }

    public Optional<String> search(String text, double threshold) {
        Map<String, Double> queryVector = embed(text);
        String bestKey = null;
        double bestScore = -1.0;
        for (Map.Entry<String, Map<String, Double>> candidate : vectorsByCacheKey.entrySet()) {
            double score = cosineSimilarity(queryVector, candidate.getValue());
            if (score > bestScore) {
                bestScore = score;
                bestKey = candidate.getKey();
            }
        }
        if (bestKey == null || bestScore < threshold) {
            return Optional.empty();
        }
        return Optional.of(bestKey);
    }

    public void remove(String cacheKey) {
        vectorsByCacheKey.remove(cacheKey);
    }

    public int clear() {
        int size = vectorsByCacheKey.size();
        vectorsByCacheKey.clear();
        return size;
    }

    private Map<String, Double> embed(String text) {
        String normalized = TextUtil.normalize(text);
        Map<String, Double> counts = new HashMap<>();
        if (normalized.isBlank()) {
            return counts;
        }
        for (String token : normalized.split("\\s+")) {
            if (token.length() < 2 || STOP_WORDS.contains(token)) {
                continue;
            }
            counts.merge(token, 1.0, Double::sum);
        }
        return counts;
    }

    private double cosineSimilarity(Map<String, Double> left, Map<String, Double> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        double dot = 0.0;
        double normLeft = 0.0;
        double normRight = 0.0;

        for (Map.Entry<String, Double> entry : left.entrySet()) {
            double leftValue = entry.getValue();
            normLeft += leftValue * leftValue;
            dot += leftValue * right.getOrDefault(entry.getKey(), 0.0);
        }
        for (double rightValue : right.values()) {
            normRight += rightValue * rightValue;
        }
        if (normLeft == 0.0 || normRight == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normLeft) * Math.sqrt(normRight));
    }
}
