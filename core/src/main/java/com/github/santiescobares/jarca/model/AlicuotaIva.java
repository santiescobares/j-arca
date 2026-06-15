package com.github.santiescobares.jarca.model;

import com.github.santiescobares.jarca.model.enums.IvaType;

import java.math.BigDecimal;

/**
 * One IVA rate entry in the Iva array (FECAEDetRequest).
 * Only relevant for class A and B comprobantes; class C must not include IVA breakdown.
 * Amounts use scale 2, HALF_UP per RN-43.
 */
public record AlicuotaIva(IvaType tipo, BigDecimal baseImponible, BigDecimal importe) {

    public AlicuotaIva {
        if (baseImponible.scale() > 2 || importe.scale() > 2) {
            throw new IllegalArgumentException("AlicuotaIva amounts must have scale <= 2");
        }
    }
}
