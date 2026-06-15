package com.github.santiescobares.jarca.model;

import java.time.LocalDate;

/**
 * CAE (Código de Autorización Electrónica) issued by ARCA upon approval.
 * The CAE is a 14-digit code that must appear on the printed/digital comprobante.
 */
public record Cae(String codigo, LocalDate vencimiento) {

    public Cae {
        if (codigo == null || codigo.length() != 14) {
            throw new IllegalArgumentException("CAE must be exactly 14 digits: " + codigo);
        }
    }
}
