package com.balancify.backend.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GroupReadCacheService {

    private static final long DEFAULT_TTL_MILLIS = 30_000L;
    // Past this many entries, those run out are dropped as the next one is kept. Visitors may ask
    // for any group number without signing in, and each number asked for leaves an entry.
    static final int SWEEP_ABOVE = 1_000;

    private final long ttlMillis;
    private final ConcurrentMap<String, CacheEntry> entries = new ConcurrentHashMap<>();

    public GroupReadCacheService(
        @Value("${balancify.cache.group-read.ttl-ms:" + DEFAULT_TTL_MILLIS + "}") long ttlMillis
    ) {
        this.ttlMillis = Math.max(0L, ttlMillis);
    }

    public <T> T get(String key, Supplier<T> loader) {
        return get(key, ttlMillis, loader);
    }

    /**
     * As get(key, loader), with the value kept for keepMillis instead of the usual time. Nothing is
     * kept while the cache is switched off (a time of 0, as tests set it).
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, long keepMillis, Supplier<T> loader) {
        long now = System.currentTimeMillis();
        CacheEntry cached = entries.get(key);
        if (cached != null && cached.expiresAtMillis() > now) {
            return (T) cached.value();
        }

        T value = loader.get();
        long keep = ttlMillis == 0L ? 0L : Math.max(0L, keepMillis);
        entries.put(key, new CacheEntry(value, now + keep));
        if (entries.size() > SWEEP_ABOVE) {
            entries.values().removeIf(entry -> entry.expiresAtMillis() <= now);
        }
        return value;
    }

    int size() {
        return entries.size();
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
