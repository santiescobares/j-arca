package com.github.santiescobares.jarca.cache;

import java.time.Duration;
import java.util.Optional;

/**
 * SPI for caching ARCA tokens and parameter tables.
 * Default implementation: {@link InMemoryArcaCache}.
 * Redis implementation: {@code RedisArcaCache} in module {@code j-arca-cache-redis}.
 *
 * <p>Key conventions:
 * <ul>
 *   <li>{@code ta:{cuit}:{servicio}} — Ticket de Acceso (e.g. {@code ta:20123456789:wsfe})</li>
 *   <li>{@code param:{servicio}:{tabla}} — WSFEv1 parameter table (e.g. {@code param:wsfe:TiposCbte})</li>
 * </ul>
 */
public interface ArcaCache {

    /**
     * Returns the cached value for {@code key}, or empty if absent or expired.
     */
    Optional<String> get(String key);

    /**
     * Stores {@code value} under {@code key} with the given TTL.
     * Replaces any existing entry for the same key.
     */
    void put(String key, String value, Duration ttl);

    /**
     * Removes the entry for {@code key} if present.
     */
    void evict(String key);
}
