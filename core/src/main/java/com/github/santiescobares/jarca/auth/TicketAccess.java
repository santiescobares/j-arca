package com.github.santiescobares.jarca.auth;

import java.time.Instant;

/**
 * Ticket de Acceso (TA) returned by WSAA's {@code loginCms} operation.
 * Contains the token and sign required for all authenticated ARCA service calls.
 *
 * @param token      opaque token string (can be several KB — never log)
 * @param sign       opaque sign string (never log)
 * @param expiresAt  when this ticket expires (UTC); derived from WSAA's {@code expirationTime}
 */
public record TicketAccess(String token, String sign, Instant expiresAt) {

    /** Returns true if this ticket is still valid with a 60-second safety margin. */
    public boolean isValid() {
        return Instant.now().isBefore(expiresAt.minusSeconds(60));
    }
}
