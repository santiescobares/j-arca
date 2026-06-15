package com.github.santiescobares.jarca.model;

import com.github.santiescobares.jarca.error.ArcaObservacion;
import com.github.santiescobares.jarca.model.enums.InvoiceResult;

import java.util.List;

/**
 * Full result of a comprobante emission attempt.
 * When {@link #resultado()} is APROBADO or APROBADO_CON_OBSERVACIONES, {@link #cae()} is non-null.
 * When RECHAZADO, {@link #cae()} is null and {@link #errores()} contains the ARCA error codes.
 */
public record ResultadoEmision(
        InvoiceResult resultado,
        long cbteNro,
        Cae cae,
        List<ArcaObservacion> observaciones,
        List<ArcaObservacion> errores
) {

    public ResultadoEmision {
        observaciones = List.copyOf(observaciones);
        errores = List.copyOf(errores);
    }

    public boolean isAprobado() {
        return resultado.isAprobado();
    }
}
