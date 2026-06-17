package com.github.santiescobares.jarca.testsupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FilePersistentArcaCacheTest {

    @Test
    void putThenGet_returnsValue(@TempDir Path dir) {
        FilePersistentArcaCache cache = new FilePersistentArcaCache(dir.resolve("cache.tsv"));

        cache.put("ta:20:wsfe", "token\nsign\n123", Duration.ofHours(1));

        assertEquals(Optional.of("token\nsign\n123"), cache.get("ta:20:wsfe"));
    }

    @Test
    void value_survivesAcrossInstances(@TempDir Path dir) {
        Path file = dir.resolve("cache.tsv");
        new FilePersistentArcaCache(file).put("k", "multi\nline\tvalue", Duration.ofHours(1));

        // A fresh instance (simulating a new JVM/run) must read the persisted value.
        assertEquals(Optional.of("multi\nline\tvalue"), new FilePersistentArcaCache(file).get("k"));
    }

    @Test
    void expiredEntry_returnsEmpty(@TempDir Path dir) {
        FilePersistentArcaCache cache = new FilePersistentArcaCache(dir.resolve("cache.tsv"));

        cache.put("k", "v", Duration.ofMillis(-1));

        assertTrue(cache.get("k").isEmpty(), "expired entry must not be returned");
    }

    @Test
    void evict_removesEntry(@TempDir Path dir) {
        FilePersistentArcaCache cache = new FilePersistentArcaCache(dir.resolve("cache.tsv"));
        cache.put("k", "v", Duration.ofHours(1));

        cache.evict("k");

        assertTrue(cache.get("k").isEmpty());
    }

    @Test
    void get_missingFile_returnsEmpty(@TempDir Path dir) {
        FilePersistentArcaCache cache = new FilePersistentArcaCache(dir.resolve("does-not-exist.tsv"));

        assertTrue(cache.get("k").isEmpty());
    }
}
