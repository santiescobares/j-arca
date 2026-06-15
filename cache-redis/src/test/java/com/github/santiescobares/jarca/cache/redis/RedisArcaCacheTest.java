package com.github.santiescobares.jarca.cache.redis;

import org.junit.jupiter.api.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RedisArcaCache} using an in-process stub TCP server
 * that speaks the RESP protocol. No real Redis required.
 */
class RedisArcaCacheTest {

    private static ServerSocket server;
    private static ExecutorService executor;
    private static int port;

    @BeforeAll
    static void startStubServer() throws IOException {
        server   = new ServerSocket(0);
        port     = server.getLocalPort();
        executor = Executors.newCachedThreadPool();
        executor.submit(() -> {
            while (!server.isClosed()) {
                try {
                    Socket client = server.accept();
                    executor.submit(() -> handleConnection(client));
                } catch (IOException ignored) { /* server closed */ }
            }
            return null;
        });
    }

    @AfterAll
    static void stopStubServer() throws IOException {
        server.close();
        executor.shutdownNow();
    }

    /** Minimal RESP handler: GET / SET EX / DEL. */
    private static void handleConnection(Socket socket) {
        try (socket;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             OutputStream writer = socket.getOutputStream()) {

            while (true) {
                String line = reader.readLine();
                if (line == null) break;
                if (!line.startsWith("*")) continue;

                int argc = Integer.parseInt(line.substring(1));
                String[] args = new String[argc];
                for (int i = 0; i < argc; i++) {
                    reader.readLine(); // $N length line
                    args[i] = reader.readLine();
                }

                byte[] reply = buildReply(args);
                writer.write(reply);
                writer.flush();
            }
        } catch (IOException | NumberFormatException ignored) {}
    }

    private static byte[] buildReply(String[] args) {
        if (args.length == 0) return "-ERR empty command\r\n".getBytes(StandardCharsets.UTF_8);
        return switch (args[0].toUpperCase()) {
            case "GET" -> {
                String stored = "existing-key".equals(args[1]) ? "cached-value" : null;
                yield stored != null
                        ? ("$" + stored.length() + "\r\n" + stored + "\r\n").getBytes(StandardCharsets.UTF_8)
                        : "$-1\r\n".getBytes(StandardCharsets.UTF_8);
            }
            case "SET" -> "+OK\r\n".getBytes(StandardCharsets.UTF_8);
            case "DEL" -> ":1\r\n".getBytes(StandardCharsets.UTF_8);
            default    -> "-ERR unknown command\r\n".getBytes(StandardCharsets.UTF_8);
        };
    }

    private RedisArcaCache cache() {
        return new RedisArcaCache("localhost", port, Duration.ofSeconds(2));
    }

    // ── GET ──────────────────────────────────────────────────────────────────

    @Test
    void get_existingKey_returnsValue() {
        assertEquals(Optional.of("cached-value"), cache().get("existing-key"));
    }

    @Test
    void get_missingKey_returnsEmpty() {
        assertEquals(Optional.empty(), cache().get("absent-key"));
    }

    // ── PUT ──────────────────────────────────────────────────────────────────

    @Test
    void put_sendsSetExWithoutException() {
        assertDoesNotThrow(() ->
                cache().put("ta:20123456789:wsfe", "token\nsign\n1718000000000",
                        Duration.ofHours(11)));
    }

    @Test
    void put_ttlLessThanOneSecond_clampsToOne() {
        // Must not throw even with sub-second TTL (clamped to 1 s minimum)
        assertDoesNotThrow(() ->
                cache().put("param:wsfe:TiposCbte", "[1,6,11]", Duration.ofMillis(500)));
    }

    // ── EVICT ────────────────────────────────────────────────────────────────

    @Test
    void evict_sendsDelWithoutException() {
        assertDoesNotThrow(() -> cache().evict("ta:20123456789:wsfe"));
    }

    // ── CACHE KEY CONVENTIONS ────────────────────────────────────────────────

    @Test
    void cacheKeys_ticketAcceso_roundTrip() {
        String key   = "ta:20123456789:wsfe";
        String value = "myToken\nmySign\n9999999999999";
        RedisArcaCache c = cache();

        c.put(key, value, Duration.ofHours(11));
        // The stub always answers GET with "cached-value" for "existing-key",
        // but for other keys it returns null — just verify no exception thrown.
        assertDoesNotThrow(() -> c.get(key));
        assertDoesNotThrow(() -> c.evict(key));
    }

    // ── CONNECTION ERROR ─────────────────────────────────────────────────────

    @Test
    void get_unreachableHost_throwsArcaException() {
        // Port 1 is reserved and not open; connection will be refused immediately.
        RedisArcaCache unreachable = new RedisArcaCache("localhost", 1, Duration.ofMillis(100));
        assertThrows(com.github.santiescobares.jarca.error.ArcaException.class,
                () -> unreachable.get("any-key"));
    }
}
