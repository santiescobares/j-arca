package com.github.santiescobares.jarca.cache;

/**
 * Canonical cache key builders. Centralises key format so changes propagate everywhere.
 */
public final class CacheKeys {

    private CacheKeys() {}

    /** Key for a Ticket de Acceso: {@code ta:{cuit}:{servicio}}. */
    public static String ticketAcceso(String cuit, String servicio) {
        return "ta:" + cuit + ":" + servicio;
    }

    /** Key for a WSFEv1 parameter table: {@code param:wsfe:{tabla}}. */
    public static String paramWsfe(String tabla) {
        return "param:wsfe:" + tabla;
    }
}
