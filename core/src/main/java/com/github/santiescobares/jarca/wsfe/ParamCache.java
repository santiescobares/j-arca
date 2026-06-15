package com.github.santiescobares.jarca.wsfe;

import com.github.santiescobares.jarca.auth.TicketAccess;
import com.github.santiescobares.jarca.cache.ArcaCache;
import com.github.santiescobares.jarca.cache.CacheKeys;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Caches WSFEv1 parameter tables obtained via {@code FEParamGet*} operations.
 *
 * <p>Parameter tables are stable: ARCA updates them infrequently. A TTL of 24 hours avoids
 * unnecessary calls while still picking up regulatory changes within a business day.
 *
 * <p>Cache key format: {@code param:wsfe:{tabla}} (see {@link CacheKeys#paramWsfe}).
 */
public final class ParamCache {

    private static final Duration PARAM_TTL = Duration.ofHours(24);

    /** Cache key suffix for the CbteTipo table. */
    private static final String KEY_TIPOS_CBTE = "tipos-cbte";

    private final WsfevClient client;
    private final ArcaCache cache;

    public ParamCache(WsfevClient client, ArcaCache cache) {
        this.client = client;
        this.cache = cache;
    }

    /**
     * Returns the list of valid {@code CbteTipo} codes supported by ARCA.
     * The result is cached for 24 hours; the first call fetches it from ARCA.
     *
     * @param ta valid Ticket de Acceso for {@code wsfe}
     */
    public List<Integer> getTiposCbte(TicketAccess ta) {
        String key = CacheKeys.paramWsfe(KEY_TIPOS_CBTE);
        Optional<String> cached = cache.get(key);
        if (cached.isPresent()) {
            return deserializeIntList(cached.get());
        }
        List<Integer> tipos = client.feParamGetTiposCbte(ta);
        cache.put(key, serializeIntList(tipos), PARAM_TTL);
        return tipos;
    }

    /**
     * Forces a refresh of all cached parameter tables.
     * Call this when a regulatory change is expected or after ARCA maintenance.
     *
     * @param ta valid Ticket de Acceso for {@code wsfe}
     */
    public void refresh(TicketAccess ta) {
        cache.evict(CacheKeys.paramWsfe(KEY_TIPOS_CBTE));
        getTiposCbte(ta);
    }

    // ── serialisation helpers (no JSON dependency) ───────────────────────────

    private static String serializeIntList(List<Integer> list) {
        return list.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static List<Integer> deserializeIntList(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .filter(s -> !s.isBlank())
                .map(Integer::parseInt)
                .collect(Collectors.toUnmodifiableList());
    }
}
