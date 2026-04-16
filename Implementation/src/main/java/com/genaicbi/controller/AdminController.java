package com.genaicbi.controller;

import com.genaicbi.model.CacheEntry;
import com.genaicbi.service.AuditService;
import com.genaicbi.service.CacheService;
import com.genaicbi.service.VectorSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/cache")
public class AdminController {

    private final CacheService cacheService;
    private final VectorSearchService vectorSearchService;
    private final AuditService auditService;

    public AdminController(CacheService cacheService, VectorSearchService vectorSearchService, AuditService auditService) {
        this.cacheService = cacheService;
        this.vectorSearchService = vectorSearchService;
        this.auditService = auditService;
    }

    @GetMapping
    public List<CacheEntry> listCache() {
        return cacheService.list();
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh() {
        int removed = cacheService.refreshExpired();
        auditService.log("admin", "cache_refresh_manual", "ok", 0L, "Removed=" + removed);
        return Map.of("removed", removed);
    }

    @PostMapping("/clear")
    public Map<String, Object> clear() {
        int cacheRemoved = cacheService.clearAll();
        int vectorsRemoved = vectorSearchService.clear();
        auditService.log("admin", "cache_clear", "ok", 0L, "cache=" + cacheRemoved + ", vectors=" + vectorsRemoved);
        return Map.of("cacheRemoved", cacheRemoved, "vectorRemoved", vectorsRemoved);
    }
}
