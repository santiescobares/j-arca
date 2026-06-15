package com.github.santiescobares.jarca.error;

/**
 * An ARCA observation or error code with its human-readable message.
 * Used both for non-fatal observations (Obs array) and for hard errors (Err array) in WSFEv1 responses.
 */
public record ArcaObservacion(int codigo, String mensaje) {

    @Override
    public String toString() {
        return "[" + codigo + "] " + mensaje;
    }
}
