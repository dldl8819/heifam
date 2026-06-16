package com.balancify.backend.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GroupReadCacheService {

    private static final long DEFAULT_TTL_MILLIS = 30_000L;

    private final long ttlMillis;
    private final ConcurrentMap<String, CacheEntry> entries = new ConcurrentHashMap<>();

    public GroupReadCacheService(
        @Value("${balancify.cache.group-read.ttl-ms:" + DEFAULT_TTL_MILLIS + "}") long ttlMillis
    ) {
        this.ttlMillis = Math.max(0L, ttlMillis);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Supplier<T> loader) {
        long now = System.currentTimeMillis();
        CacheEntry cached = entries.get(key);
        if (cached != null && cached.expiresAtMillis() > now) {
            return (T) cached.value();
        }

        T value = loader.get();
        entries.put(key, new CacheEntry(value, now + ttlMillis));
        return value;
    }

    public void evictGroup(Long groupId) {
        if (groupId == null) {
            return;
        }

        String groupToken = ":group:" + groupId + ":";
        entries.keySet().removeIf(key -> key.contains(groupToken));
    }

    public void clearAll() {
        entries.clear();
    }

    private record CacheEntry(Object value, long expiresAtMillis) {
    }
}
