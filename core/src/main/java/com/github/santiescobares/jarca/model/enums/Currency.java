package com.github.santiescobares.jarca.model.enums;

/**
 * Moneda del comprobante (MonId). v1 only handles PES (Pesos argentinos).
 * Other currencies require MonCotiz != 1 and are out of v1 scope.
 */
public enum Currency {

    PESOS("PES", "Pesos Argentinos"),
    DOLARES_EEUU("DOL", "Dólares Estadounidenses"),
    EUROS("060", "Euros");

    private final String codigo;
    private final String descripcion;

    Currency(String codigo, String descripcion) {
        this.codigo = codigo;
        this.descripcion = descripcion;
    }

    /** ARCA string code for MonId. */
    public String getCodigo() { return codigo; }

    public String getDescripcion() { return descripcion; }

    public static Currency fromCodigo(String codigo) {
        for (Currency c : values()) {
            if (c.codigo.equalsIgnoreCase(codigo)) return c;
        }
        throw new IllegalArgumentException("Unknown Currency code: " + codigo);
    }
}
