package com.github.santiescobares.jarca.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryArcaCacheTest {

    @Test
    void putAndGet_returnsValue() {
        InMemoryArcaCache cache = new InMemoryArcaCache();
        cache.put("k", "v", Duration.ofMinutes(10));
        assertEquals(Optional.of("v"), cache.get("k"));
    }

    @Test
    void get_afterEvict_returnsEmpty() {
        InMemoryArcaCache cache = new InMemoryArcaCache();
        cache.put("k", "v", Duration.ofMinutes(10));
        cache.evict("k");
        assertEquals(Optional.empty(), cache.get("k"));
    }

    @Test
    void get_afterExpiry_returnsEmpty() throws InterruptedException {
        InMemoryArcaCache cache = new InMemoryArcaCache();
        cache.put("k", "v", Duration.ofMillis(50));
        Thread.sleep(100);
        assertEquals(Optional.empty(), cache.get("k"));
    }

    @Test
    void put_replacesExisting() {
        InMemoryArcaCache cache = new InMemoryArcaCache();
        cache.put("k", "first",  Duration.ofMinutes(10));
        cache.put("k", "second", Duration.ofMinutes(10));
        assertEquals(Optional.of("second"), cache.get("k"));
    }

    @Test
    void get_missingKey_returnsEmpty() {
        assertEquals(Optional.empty(), new InMemoryArcaCache().get("nonexistent"));
    }
}
