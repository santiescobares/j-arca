package com.github.santiescobares.jarca.model.enums;

/**
 * Tipo de documento del receptor (DocTipo).
 * Clase A requires DocTipo=80 (CUIT). Clase C allows DocTipo=99 (consumidor final).
 */
public enum IdType {

    CUIT(80),
    CUIL(86),
    CDI(87),
    LE(89),
    LC(90),
    CI_EXTRANJERA(91),
    EN_TRAMITE(92),
    ACTA_NACIMIENTO(93),
    CI_BS_AS_RNP(95),
    DNI(96),
    PASAPORTE(94),
    DOC_SIN_NUMERO(99);   // consumidor final — DocNro = 0

    private final int codigo;

    IdType(int codigo) {
        this.codigo = codigo;
    }

    /** ARCA numeric code for this document type. */
    public int getCodigo() { return codigo; }

    public static IdType fromCodigo(int codigo) {
        for (IdType t : values()) {
            if (t.codigo == codigo) return t;
        }
        throw new IllegalArgumentException("Unknown DocTipo: " + codigo);
    }
}
