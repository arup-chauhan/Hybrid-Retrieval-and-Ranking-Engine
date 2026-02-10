package com.hybrid.query.service;

import com.hybrid.query.model.QueryResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class HybridQueryCacheService {

    private final boolean enabled;
    private final long ttlMillis;
    private final int maxEntries;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public HybridQueryCacheService(
            @Value("${query.cache.enabled:true}") boolean enabled,
            @Value("${query.cache.ttl-seconds:120}") long ttlSeconds,
            @Value("${query.cache.max-entries:5000}") int maxEntries
    ) {
        this.enabled = enabled;
        this.ttlMillis = Math.max(1L, ttlSeconds) * 1000L;
        this.maxEntries = Math.max(100, maxEntries);
    }

    public QueryResult get(String query, int topK, String mode, String filter) {
        if (!enabled) {
            return null;
        }
        CacheEntry entry = cache.get(key(query, topK, mode, filter));
        if (entry == null) {
            return null;
        }
        if (entry.expiresAtMillis <= System.currentTimeMillis()) {
            cache.remove(key(query, topK, mode, filter));
            return null;
        }
        return entry.value;
    }

    public void put(String query, int topK, String mode, String filter, QueryResult result) {
        if (!enabled || result == null) {
            return;
        }
        if (cache.size() >= maxEntries) {
            cache.clear();
        }
        cache.put(key(query, topK, mode, filter), new CacheEntry(result, System.currentTimeMillis() + ttlMillis));
    }

    private static String key(String query, int topK, String mode, String filter) {
        return (query == null ? "" : query)
                + "::" + topK
                + "::" + mode
                + "::" + filter;
    }

    private record CacheEntry(QueryResult value, long expiresAtMillis) {
    }
}
