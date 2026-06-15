package com.github.santiescobares.jarca.error;

import java.util.List;

/**
 * Thrown when ARCA explicitly rejects a request (Resultado = "R" or a non-200 SOAP fault).
 * Contains the full list of error codes and, when present, per-comprobante observations
 * ({@code FECAEDetResponse/Observaciones/Obs}) that explain the rejection.
 */
public class ArcaRechazo extends ArcaException {

    private final List<ArcaObservacion> errores;
    private final List<ArcaObservacion> obs;

    public ArcaRechazo(List<ArcaObservacion> errores) {
        this(errores, List.of());
    }

    public ArcaRechazo(List<ArcaObservacion> errores, List<ArcaObservacion> obs) {
        super("ARCA rechazó la solicitud: errores=" + errores + " obs=" + obs);
        this.errores = List.copyOf(errores);
        this.obs = List.copyOf(obs);
    }

    /** Immutable list of ARCA global error codes returned in the response. */
    public List<ArcaObservacion> getErrores() {
        return errores;
    }

    /**
     * Immutable list of per-comprobante observations ({@code Obs}) that accompanied the rejection.
     * Non-empty when the rejection reason is reported via {@code FECAEDetResponse/Observaciones}
     * rather than the global {@code Errors} block (e.g. error 10016).
     */
    public List<ArcaObservacion> getObs() {
        return obs;
    }

    /** Returns true if any error or observation has the given ARCA code. */
    public boolean tieneError(int codigo) {
        return errores.stream().anyMatch(e -> e.codigo() == codigo)
                || obs.stream().anyMatch(e -> e.codigo() == codigo);
    }
}
