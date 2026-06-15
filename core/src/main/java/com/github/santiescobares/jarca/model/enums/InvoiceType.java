package com.github.santiescobares.jarca.model.enums;

/**
 * Tipo de comprobante ARCA (CbteTipo). Subset soportado en v1: A, B y C.
 * Código numérico según tabla oficial de tipos de comprobante.
 */
public enum InvoiceType {

    FACTURA_A(1),
    NOTA_DEBITO_A(2),
    NOTA_CREDITO_A(3),

    FACTURA_B(6),
    NOTA_DEBITO_B(7),
    NOTA_CREDITO_B(8),

    FACTURA_C(11),
    NOTA_DEBITO_C(12),
    NOTA_CREDITO_C(13);

    private final int codigo;

    InvoiceType(int codigo) {
        this.codigo = codigo;
    }

    /** ARCA numeric code for this invoice type (CbteTipo). */
    public int getCodigo() { return codigo; }

    /** Whether this type is a Nota de Crédito (reverses a parent comprobante). */
    public boolean isNotaCredito() {
        return this == NOTA_CREDITO_A || this == NOTA_CREDITO_B || this == NOTA_CREDITO_C;
    }

    /** Whether this type is a Nota de Débito. */
    public boolean isNotaDebito() {
        return this == NOTA_DEBITO_A || this == NOTA_DEBITO_B || this == NOTA_DEBITO_C;
    }

    /** Returns the letter class of this type: 'A', 'B', or 'C'. */
    public char getClase() {
        return switch (this) {
            case FACTURA_A, NOTA_DEBITO_A, NOTA_CREDITO_A -> 'A';
            case FACTURA_B, NOTA_DEBITO_B, NOTA_CREDITO_B -> 'B';
            case FACTURA_C, NOTA_DEBITO_C, NOTA_CREDITO_C -> 'C';
        };
    }

    public static InvoiceType fromCodigo(int codigo) {
        for (InvoiceType t : values()) {
            if (t.codigo == codigo) return t;
        }
        throw new IllegalArgumentException("Unknown CbteTipo: " + codigo);
    }
}
