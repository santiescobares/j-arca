package com.github.santiescobares.jarca.model.enums;

/**
 * Alícuota de IVA (Id de AlicIva). Used in the Iva array for class A/B comprobantes.
 * Class C comprobantes must NOT include this array.
 */
public enum IvaType {

    IVA_0(3, "0%"),
    IVA_10_5(4, "10.5%"),
    IVA_21(5, "21%"),
    IVA_27(6, "27%"),
    IVA_5(8, "5%"),
    IVA_2_5(9, "2.5%");

    private final int codigo;
    private final String descripcion;

    IvaType(int codigo, String descripcion) {
        this.codigo = codigo;
        this.descripcion = descripcion;
    }

    /** ARCA numeric id for this IVA rate (AlicIva.Id). */
    public int getCodigo() { return codigo; }

    public String getDescripcion() { return descripcion; }

    public static IvaType fromCodigo(int codigo) {
        for (IvaType t : values()) {
            if (t.codigo == codigo) return t;
        }
        throw new IllegalArgumentException("Unknown IVA type code: " + codigo);
    }
}
