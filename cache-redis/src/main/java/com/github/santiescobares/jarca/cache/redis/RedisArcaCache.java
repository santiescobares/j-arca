package com.github.santiescobares.jarca.cache.redis;

import com.github.santiescobares.jarca.cache.ArcaCache;
import com.github.santiescobares.jarca.error.ArcaException;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

/**
 * {@link ArcaCache} backed by Redis, using a zero-dependency RESP client ({@link RespClient}).
 * Each operation opens and closes its own connection.
 *
 * <p>Construction:
 * <pre>{@code
 * RedisArcaCache cache = new RedisArcaCache("localhost", 6379);
 * }</pre>
 */
public final class RedisArcaCache implements ArcaCache {

    private final String host;
    private final int port;
    private final Duration connectTimeout;

    public RedisArcaCache(String host, int port) {
        this(host, port, Duration.ofSeconds(5));
    }

    public RedisArcaCache(String host, int port, Duration connectTimeout) {
        this.host = host;
        this.port = port;
        this.connectTimeout = connectTimeout;
    }

    @Override
    public Optional<String> get(String key) {
        try (RespClient client = connect()) {
            return Optional.ofNullable(client.get(key));
        } catch (IOException e) {
            throw new ArcaException("Redis GET failed for key: " + key, e);
        }
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        try (RespClient client = connect()) {
            client.setEx(key, value, Math.max(1L, ttl.toSeconds()));
        } catch (IOException e) {
            throw new ArcaException("Redis SET failed for key: " + key, e);
        }
    }

    @Override
    public void evict(String key) {
        try (RespClient client = connect()) {
            client.del(key);
        } catch (IOException e) {
            throw new ArcaException("Redis DEL failed for key: " + key, e);
        }
    }

    private RespClient connect() throws IOException {
        RespClient client = new RespClient(host, port, connectTimeout);
        client.connect();
        return client;
    }
}
