package com.genaicbi.service;

import com.genaicbi.dto.AskQuestionResponse;
import com.genaicbi.model.ChatHistoryEntry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class ConversationService {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<ChatHistoryEntry>> historyByUser = new ConcurrentHashMap<>();

    public void append(String userId, String question, AskQuestionResponse response) {
        String safeUser = sanitizeUser(userId);
        ChatHistoryEntry entry = new ChatHistoryEntry(
                UUID.randomUUID().toString(),
                safeUser,
                question,
                response.cacheKey(),
                response.source(),
                response.fromCache(),
                response.rowCount(),
                response.confidenceScore(),
                response.latencyMs(),
                response.summary(),
                Instant.now()
        );
        historyByUser.computeIfAbsent(safeUser, ignore -> new CopyOnWriteArrayList<>()).add(entry);
    }

    public List<ChatHistoryEntry> listForUser(String userId, int limit) {
        String safeUser = sanitizeUser(userId);
        int safeLimit = Math.max(1, limit);
        List<ChatHistoryEntry> entries = new ArrayList<>(historyByUser.getOrDefault(safeUser, new CopyOnWriteArrayList<>()));
        entries.sort(Comparator.comparing(ChatHistoryEntry::createdAt).reversed());
        return entries.stream().limit(safeLimit).toList();
    }

    public ConversationStats statsForUser(String userId) {
        String safeUser = sanitizeUser(userId);
        List<ChatHistoryEntry> entries = historyByUser.getOrDefault(safeUser, new CopyOnWriteArrayList<>());
        if (entries.isEmpty()) {
            return new ConversationStats(0, 0.0, 0.0);
        }
        int total = entries.size();
        long cacheHits = entries.stream().filter(ChatHistoryEntry::fromCache).count();
        double cacheHitRate = (cacheHits * 100.0) / total;
        double avgLatency = entries.stream().mapToLong(ChatHistoryEntry::latencyMs).average().orElse(0.0);
        return new ConversationStats(total, cacheHitRate, avgLatency);
    }

    private String sanitizeUser(String userId) {
        return userId == null || userId.isBlank() ? "anonymous" : userId;
    }

    public record ConversationStats(
            int totalQuestions,
            double cacheHitRate,
            double averageLatencyMs
    ) {
    }
}
