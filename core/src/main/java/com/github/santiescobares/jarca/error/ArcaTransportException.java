package com.github.santiescobares.jarca.error;

/**
 * Thrown when a network, TLS, or HTTP-level failure occurs communicating with ARCA.
 * The comprobante was NOT submitted — safe to retry (check idempotency first with FECompConsultar).
 */
public class ArcaTransportException extends ArcaException {

    public ArcaTransportException(String message) {
        super(message);
    }

    public ArcaTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
