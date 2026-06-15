package com.github.santiescobares.jarca.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, in-process cache backed by a {@link ConcurrentHashMap} with TTL support.
 * Expired entries are evicted lazily on {@link #get}.
 * Use this when Redis is not available or not desired.
 */
public final class InMemoryArcaCache implements ArcaCache {

    private record Entry(String value, Instant expiresAt) {
        boolean isAlive() { return Instant.now().isBefore(expiresAt); }
    }

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public Optional<String> get(String key) {
        Entry entry = store.get(key);
        if (entry == null) return Optional.empty();
        if (!entry.isAlive()) {
            store.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        store.put(key, new Entry(value, Instant.now().plus(ttl)));
    }

    @Override
    public void evict(String key) {
        store.remove(key);
    }

    /** Exposed for tests: current number of (possibly expired) entries. */
    int size() { return store.size(); }
}
