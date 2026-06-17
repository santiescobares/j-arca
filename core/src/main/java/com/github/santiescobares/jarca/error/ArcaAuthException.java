package com.github.santiescobares.jarca.error;

/**
 * Thrown when WSAA refuses to issue a Ticket de Acceso because a valid one already exists for the
 * {@code (CUIT, servicio)} pair (fault {@code coe.alreadyAuthenticated}).
 *
 * <p>WSAA allows only one valid TA per {@code (CUIT, servicio)} at a time and will not issue another
 * until the current one expires (up to the TRA validity window, by default 12&nbsp;h). The caller
 * must reuse the cached TA instead of logging in again. In practice this fault signals an
 * {@link com.github.santiescobares.jarca.cache.ArcaCache} that is not shared or not persisted across
 * instances/restarts.
 */
public class ArcaAuthException extends ArcaException {

    public ArcaAuthException(String message) {
        super(message);
    }

    public ArcaAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
