package com.github.santiescobares.jarca.model.enums;

/**
 * Resultado de la operación de emisión (Resultado).
 * A = Aprobado (CAE emitted), O = Aprobado con observaciones, P = Parcialmente aprobado, R = Rechazado.
 */
public enum InvoiceResult {

    /** Approved — CAE was issued. */
    APROBADO("A"),
    /** Approved with observations — CAE was issued but ARCA flagged warnings. */
    APROBADO_CON_OBSERVACIONES("O"),
    /** Partially approved — CAE was issued for some items; check observaciones for details. */
    PARCIAL("P"),
    /** Rejected — no CAE issued; check errores for reason. */
    RECHAZADO("R");

    private final String codigo;

    InvoiceResult(String codigo) {
        this.codigo = codigo;
    }

    public String getCodigo() { return codigo; }

    public boolean isAprobado() {
        return this == APROBADO || this == APROBADO_CON_OBSERVACIONES;
    }

    public static InvoiceResult fromCodigo(String codigo) {
        for (InvoiceResult r : values()) {
            if (r.codigo.equalsIgnoreCase(codigo)) return r;
        }
        throw new IllegalArgumentException("Unknown Resultado code: " + codigo);
    }
}
