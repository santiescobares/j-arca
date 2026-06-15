package com.github.santiescobares.jarca.model;

import com.github.santiescobares.jarca.model.enums.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnumMappingTest {

    @Test
    void invoiceType_roundtrip() {
        for (InvoiceType t : InvoiceType.values()) {
            assertEquals(t, InvoiceType.fromCodigo(t.getCodigo()),
                    "Round-trip failed for " + t);
        }
    }

    @Test
    void invoiceType_claseC_isNotaCredito() {
        assertTrue(InvoiceType.NOTA_CREDITO_C.isNotaCredito());
        assertFalse(InvoiceType.FACTURA_C.isNotaCredito());
    }

    @Test
    void invoiceType_clases() {
        assertEquals('A', InvoiceType.FACTURA_A.getClase());
        assertEquals('B', InvoiceType.NOTA_DEBITO_B.getClase());
        assertEquals('C', InvoiceType.NOTA_CREDITO_C.getClase());
    }

    @Test
    void idType_roundtrip() {
        for (IdType t : IdType.values()) {
            assertEquals(t, IdType.fromCodigo(t.getCodigo()));
        }
    }

    @Test
    void ivaType_roundtrip() {
        for (IvaType t : IvaType.values()) {
            assertEquals(t, IvaType.fromCodigo(t.getCodigo()));
        }
    }

    @Test
    void ivaCondition_codesMatchOfficialTable() {
        assertEquals(1,  IvaCondition.IVA_RESPONSABLE_INSCRIPTO.getCodigo());
        assertEquals(4,  IvaCondition.IVA_EXENTO.getCodigo());
        assertEquals(5,  IvaCondition.CONSUMIDOR_FINAL.getCodigo());
        assertEquals(6,  IvaCondition.RESPONSABLE_MONOTRIBUTO.getCodigo());
        assertEquals(13, IvaCondition.MONOTRIBUTISTA_SOCIAL.getCodigo());
        assertEquals(15, IvaCondition.IVA_NO_ALCANZADO.getCodigo());
        assertEquals(16, IvaCondition.ADHERENTE_MONOTRIBUTO.getCodigo());
    }

    @Test
    void ivaCondition_roundtrip() {
        for (IvaCondition c : IvaCondition.values()) {
            assertEquals(c, IvaCondition.fromCodigo(c.getCodigo()));
        }
    }

    @Test
    void ivaCondition_invalidCodesAbsent() {
        // Codes 2, 11, 12, 14 are not in the FEParamGetCondicionIvaReceptor table
        for (int invalid : new int[]{2, 11, 12, 14}) {
            final int code = invalid;
            assertThrows(IllegalArgumentException.class,
                    () -> IvaCondition.fromCodigo(code),
                    "Code " + code + " should not exist");
        }
    }

    @Test
    void invoiceConcept_serviciosRequiresDates() {
        assertTrue(InvoiceConcept.SERVICIOS.requiresServiceDates());
        assertFalse(InvoiceConcept.PRODUCTOS.requiresServiceDates());
    }

    @Test
    void currency_pesos() {
        assertEquals("PES", Currency.PESOS.getCodigo());
        assertEquals(Currency.PESOS, Currency.fromCodigo("PES"));
    }

    @Test
    void invoiceResult_aprobado() {
        assertTrue(InvoiceResult.APROBADO.isAprobado());
        assertTrue(InvoiceResult.APROBADO_CON_OBSERVACIONES.isAprobado());
        assertFalse(InvoiceResult.RECHAZADO.isAprobado());
        assertFalse(InvoiceResult.PARCIAL.isAprobado());
    }

    @Test
    void invoiceResult_roundtrip() {
        assertEquals(InvoiceResult.APROBADO,   InvoiceResult.fromCodigo("A"));
        assertEquals(InvoiceResult.RECHAZADO,  InvoiceResult.fromCodigo("R"));
        assertEquals(InvoiceResult.PARCIAL,    InvoiceResult.fromCodigo("P"));
    }

    @Test
    void unknownCodigo_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> InvoiceType.fromCodigo(999));
        assertThrows(IllegalArgumentException.class, () -> IdType.fromCodigo(999));
        assertThrows(IllegalArgumentException.class, () -> IvaType.fromCodigo(999));
        assertThrows(IllegalArgumentException.class, () -> IvaCondition.fromCodigo(999));
    }
}
