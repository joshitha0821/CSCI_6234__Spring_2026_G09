package com.genaicbi.model;

import com.genaicbi.dto.ChartPayload;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class CacheEntry {

    private final String key;
    private final String normalizedQuestion;
    private final String sql;
    private final List<String> columns;
    private final List<Map<String, Object>> rows;
    private final ChartPayload chart;
    private final String summary;
    private final Instant createdAt;
    private Instant expiresAt;
    private Instant lastAccess;
    private int hits;
    private int trustScore;

    public CacheEntry(
            String key,
            String normalizedQuestion,
            String sql,
            List<String> columns,
            List<Map<String, Object>> rows,
            ChartPayload chart,
            String summary,
            Instant createdAt,
            Instant expiresAt
    ) {
        this.key = key;
        this.normalizedQuestion = normalizedQuestion;
        this.sql = sql;
        this.columns = columns;
        this.rows = rows;
        this.chart = chart;
        this.summary = summary;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.lastAccess = createdAt;
        this.hits = 0;
        this.trustScore = 0;
    }

    public synchronized boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public synchronized void markHit(Instant now) {
        this.hits += 1;
        this.lastAccess = now;
    }

    public synchronized void applyFeedback(VoteType vote) {
        if (vote == VoteType.APPROVE) {
            this.trustScore += 1;
        } else {
            this.trustScore -= 1;
        }
    }

    public synchronized void extendTtl(Instant newExpiry) {
        this.expiresAt = newExpiry;
    }

    public String getKey() {
        return key;
    }

    public String getNormalizedQuestion() {
        return normalizedQuestion;
    }

    public String getSql() {
        return sql;
    }

    public List<String> getColumns() {
        return columns;
    }

    public List<Map<String, Object>> getRows() {
        return rows;
    }

    public ChartPayload getChart() {
        return chart;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public synchronized Instant getExpiresAt() {
        return expiresAt;
    }

    public synchronized Instant getLastAccess() {
        return lastAccess;
    }

    public synchronized int getHits() {
        return hits;
    }

    public synchronized int getTrustScore() {
        return trustScore;
    }
}
