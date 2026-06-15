package com.github.santiescobares.jarca.model;

import com.github.santiescobares.jarca.model.enums.InvoiceType;

/**
 * Reference to the parent comprobante for Notas de Crédito/Débito (CbtesAsoc).
 * Required when the comprobante type is a nota.
 */
public record CbteAsociado(InvoiceType tipo, int ptoVta, long nroCbte) {
}
