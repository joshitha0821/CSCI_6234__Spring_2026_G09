package com.genaicbi.service;

import com.genaicbi.model.AuditEvent;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class AuditService {

    private final CopyOnWriteArrayList<AuditEvent> events = new CopyOnWriteArrayList<>();

    public void log(String userId, String action, String status, long latencyMs, String message) {
        events.add(new AuditEvent(
                UUID.randomUUID().toString(),
                userId == null || userId.isBlank() ? "anonymous" : userId,
                action,
                status,
                latencyMs,
                message,
                Instant.now()
        ));
    }

    public List<AuditEvent> latest(int limit) {
        int max = Math.max(1, limit);
        List<AuditEvent> copy = new ArrayList<>(events);
        copy.sort(Comparator.comparing(AuditEvent::timestamp).reversed());
        return copy.stream().limit(max).toList();
    }
}
