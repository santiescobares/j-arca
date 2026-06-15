package com.github.santiescobares.jarca.model.enums;

/**
 * Concepto del comprobante (Concepto).
 * When 2 (Servicios) or 3 (ProductosYServicios), service date fields are mandatory.
 */
public enum InvoiceConcept {

    PRODUCTOS(1),
    SERVICIOS(2),
    PRODUCTOS_Y_SERVICIOS(3);

    private final int codigo;

    InvoiceConcept(int codigo) {
        this.codigo = codigo;
    }

    public int getCodigo() { return codigo; }

    /** Whether service period dates (FchServDesde, FchServHasta, FchVtoPago) are required. */
    public boolean requiresServiceDates() {
        return this == SERVICIOS || this == PRODUCTOS_Y_SERVICIOS;
    }

    public static InvoiceConcept fromCodigo(int codigo) {
        for (InvoiceConcept c : values()) {
            if (c.codigo == codigo) return c;
        }
        throw new IllegalArgumentException("Unknown Concepto code: " + codigo);
    }
}
