package com.github.santiescobares.jarca.model.enums;

/**
 * Condición IVA del receptor (CondicionIVAReceptorId), obligatorio desde RG 5616.
 * Used in FECAEDetRequest. Derived from Padrón data via CondicionIvaResolver.
 *
 * <p>Codes reflect the {@code FEParamGetCondicionIvaReceptor} table from the WSFEv1 WSDL.
 * Codes 2 (No Inscripto), 11 (Agente Percepción), 12 (Pequeño Contribuyente Eventual),
 * and 14 (Pequeño Contribuyente Eventual Social) are absent from the official table and
 * must not be sent to ARCA.
 */
public enum IvaCondition {

    IVA_RESPONSABLE_INSCRIPTO(1),
    IVA_EXENTO(4),
    CONSUMIDOR_FINAL(5),
    RESPONSABLE_MONOTRIBUTO(6),
    SUJETO_NO_CATEGORIZADO(7),
    IMPORTADOR_DEL_EXTERIOR(8),
    CLIENTE_DEL_EXTERIOR(9),
    IVA_LIBERADO_LEY_19640(10),
    MONOTRIBUTISTA_SOCIAL(13),
    IVA_NO_ALCANZADO(15),
    ADHERENTE_MONOTRIBUTO(16);

    private final int codigo;

    IvaCondition(int codigo) {
        this.codigo = codigo;
    }

    /** ARCA numeric code for CondicionIVAReceptorId. */
    public int getCodigo() { return codigo; }

    public static IvaCondition fromCodigo(int codigo) {
        for (IvaCondition c : values()) {
            if (c.codigo == codigo) return c;
        }
        throw new IllegalArgumentException("Unknown IvaCondition code: " + codigo);
    }
}
