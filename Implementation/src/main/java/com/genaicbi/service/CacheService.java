package com.genaicbi.service;

import com.genaicbi.model.CacheEntry;
import com.genaicbi.model.VoteType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CacheService {

    private static final int LOW_CONFIDENCE_THRESHOLD = -2;

    private final ConcurrentHashMap<String, CacheEntry> cacheByKey = new ConcurrentHashMap<>();
    private final Duration ttl;

    public CacheService(@Value("${app.cache.ttl-minutes:1440}") long ttlMinutes) {
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public Optional<CacheEntry> getDirect(String key) {
        CacheEntry entry = cacheByKey.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        if (entry.isExpired(now)) {
            cacheByKey.remove(key);
            return Optional.empty();
        }
        entry.markHit(now);
        return Optional.of(entry);
    }

    public Optional<CacheEntry> getByKey(String key, boolean markHit) {
        CacheEntry entry = cacheByKey.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        if (entry.isExpired(now)) {
            cacheByKey.remove(key);
            return Optional.empty();
        }
        if (markHit) {
            entry.markHit(now);
        }
        return Optional.of(entry);
    }

    public CacheEntry put(CacheEntry entry) {
        cacheByKey.put(entry.getKey(), entry);
        return entry;
    }

    public Instant defaultExpiryFromNow() {
        return Instant.now().plus(ttl);
    }

    public int refreshExpired() {
        Instant now = Instant.now();
        int removed = 0;
        for (String key : cacheByKey.keySet()) {
            CacheEntry entry = cacheByKey.get(key);
            if (entry == null) {
                continue;
            }
            if (entry.isExpired(now) || entry.getTrustScore() <= LOW_CONFIDENCE_THRESHOLD) {
                if (cacheByKey.remove(key) != null) {
                    removed++;
                }
            }
        }
        return removed;
    }

    public int clearAll() {
        int size = cacheByKey.size();
        cacheByKey.clear();
        return size;
    }

    public void applyFeedback(String cacheKey, VoteType vote) {
        CacheEntry entry = cacheByKey.get(cacheKey);
        if (entry != null) {
            entry.applyFeedback(vote);
        }
    }

    public List<CacheEntry> list() {
        return new ArrayList<>(cacheByKey.values());
    }
}
