package com.github.santiescobares.jarca.wsfe;

import com.github.santiescobares.jarca.model.Comprobante;
import com.github.santiescobares.jarca.model.ResultadoEmision;

/**
 * High-level entry point for comprobante emission.
 * Orchestrates: last-authorised lookup → CAE request → result mapping → idempotency guard.
 */
public interface ComprobanteService {

    /**
     * Emits {@code comprobante} and returns the ARCA result (CAE or rejection).
     * Blocks until ARCA responds; does not queue retries.
     *
     * @throws com.github.santiescobares.jarca.error.ArcaRechazo           if ARCA rejects
     * @throws com.github.santiescobares.jarca.error.ArcaTransportException on network failure
     */
    ResultadoEmision emitir(Comprobante comprobante);
}
