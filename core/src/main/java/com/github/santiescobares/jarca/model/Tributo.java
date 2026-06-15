package com.github.santiescobares.jarca.model;

import java.math.BigDecimal;

/**
 * Other tax (tributo) included in ImpTrib. Id according to ARCA table of tributos.
 */
public record Tributo(int id, String descripcion, BigDecimal baseImponible,
                      BigDecimal alicuota, BigDecimal importe) {
}
