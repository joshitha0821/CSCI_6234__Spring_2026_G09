package com.genaicbi.scheduler;

import com.genaicbi.service.AuditService;
import com.genaicbi.service.CacheService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CacheMaintenanceScheduler {

    private final CacheService cacheService;
    private final AuditService auditService;

    public CacheMaintenanceScheduler(CacheService cacheService, AuditService auditService) {
        this.cacheService = cacheService;
        this.auditService = auditService;
    }

    @Scheduled(fixedDelayString = "${app.cache.refresh-ms:60000}")
    public void refreshCache() {
        int removed = cacheService.refreshExpired();
        if (removed > 0) {
            auditService.log("system", "cache_refresh", "ok", 0L, "Purged " + removed + " stale cache entries");
        }
    }
}
