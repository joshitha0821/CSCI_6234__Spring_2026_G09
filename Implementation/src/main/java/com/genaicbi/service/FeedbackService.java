package com.genaicbi.service;

import com.genaicbi.model.FeedbackRecord;
import com.genaicbi.model.VoteType;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class FeedbackService {

    private final CopyOnWriteArrayList<FeedbackRecord> feedbackRecords = new CopyOnWriteArrayList<>();
    private final CacheService cacheService;
    private final AuditService auditService;

    public FeedbackService(CacheService cacheService, AuditService auditService) {
        this.cacheService = cacheService;
        this.auditService = auditService;
    }

    public FeedbackRecord record(String cacheKey, VoteType vote, String userId, String comment) {
        FeedbackRecord record = new FeedbackRecord(
                UUID.randomUUID().toString(),
                cacheKey,
                vote,
                userId == null || userId.isBlank() ? "anonymous" : userId,
                comment,
                Instant.now()
        );
        feedbackRecords.add(record);
        cacheService.applyFeedback(cacheKey, vote);
        auditService.log(userId, "feedback", "ok", 0L, "Vote=" + vote + " for cacheKey=" + cacheKey);
        return record;
    }

    public List<FeedbackRecord> list() {
        return feedbackRecords.stream()
                .sorted(Comparator.comparing(FeedbackRecord::createdAt).reversed())
                .toList();
    }
}
