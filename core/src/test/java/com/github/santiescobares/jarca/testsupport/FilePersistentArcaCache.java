package com.github.santiescobares.jarca.testsupport;

import com.github.santiescobares.jarca.cache.ArcaCache;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * File-backed {@link ArcaCache} used only by the integration tests.
 *
 * <p>WSAA issues a single valid Ticket de Acceso (TA) per {@code (CUIT, servicio)} and rejects any
 * further {@code loginCms} with {@code coe.alreadyAuthenticated} until the current TA expires (up to
 * 12 h). The integration tests therefore must reuse one TA across every IT class and across repeated
 * {@code mvn verify -Phomologacion} runs. An in-memory cache cannot do that — it is lost when the
 * forked JVM exits, so the next run logs in again and is rejected.
 *
 * <p>This implementation persists entries to a file under {@code target/} (cleared by
 * {@code mvn clean}, ignored by Git), mirroring how {@code RedisArcaCache} persists the TA in
 * production. The TA's {@code token}/{@code sign} are stored Base64-encoded for line safety, not for
 * confidentiality; the file is transient build output and must not be committed.
 *
 * <p>Entry format, one line per key: {@code key \t base64(value) \t expiryEpochMillis}.
 */
public final class FilePersistentArcaCache implements ArcaCache {

    private final Path file;

    public FilePersistentArcaCache() {
        this(Paths.get(System.getProperty("arca.it.cacheFile", "target/arca-it-cache.tsv")));
    }

    public FilePersistentArcaCache(Path file) {
        this.file = file;
    }

    @Override
    public synchronized Optional<String> get(String key) {
        Map<String, Entry> all = load();
        Entry e = all.get(key);
        if (e == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(e.expiresAt())) {
            all.remove(key);
            store(all);
            return Optional.empty();
        }
        return Optional.of(e.value());
    }

    @Override
    public synchronized void put(String key, String value, Duration ttl) {
        Map<String, Entry> all = load();
        all.put(key, new Entry(value, Instant.now().plus(ttl)));
        store(all);
    }

    @Override
    public synchronized void evict(String key) {
        Map<String, Entry> all = load();
        if (all.remove(key) != null) {
            store(all);
        }
    }

    private record Entry(String value, Instant expiresAt) {}

    private Map<String, Entry> load() {
        Map<String, Entry> map = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return map;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                String[] p = line.split("\t", 3);
                if (p.length != 3) {
                    continue;
                }
                String value = new String(Base64.getDecoder().decode(p[1]), StandardCharsets.UTF_8);
                map.put(p[0], new Entry(value, Instant.ofEpochMilli(Long.parseLong(p[2]))));
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read IT cache file " + file, ex);
        }
        return map;
    }

    private void store(Map<String, Entry> all) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Entry> e : all.entrySet()) {
            String b64 = Base64.getEncoder()
                    .encodeToString(e.getValue().value().getBytes(StandardCharsets.UTF_8));
            sb.append(e.getKey()).append('\t')
              .append(b64).append('\t')
              .append(e.getValue().expiresAt().toEpochMilli()).append('\n');
        }
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to write IT cache file " + file, ex);
        }
    }
}
