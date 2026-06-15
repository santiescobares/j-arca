package com.github.santiescobares.jarca.wsfe;

import com.github.santiescobares.jarca.auth.TicketAccess;
import com.github.santiescobares.jarca.error.ArcaException;
import com.github.santiescobares.jarca.model.AlicuotaIva;
import com.github.santiescobares.jarca.model.CbteAsociado;
import com.github.santiescobares.jarca.model.Comprobante;
import com.github.santiescobares.jarca.model.enums.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FeCaeMapper — verifies the generated XML structure and class-specific rules.
 */
class FeCaeMapperTest {

    private static final TicketAccess FAKE_TA =
            new TicketAccess("TK", "SG", Instant.now().plusSeconds(3600));
    private static final String CUIT = "20123456789";

    // ── Factura C ─────────────────────────────────────────────────────────────

    @Test
    void facturaC_buildsBodyWithZeroIvaFields() {
        Comprobante cbte = facturaC(new BigDecimal("1000.00"));
        String xml = FeCaeMapper.buildBody(CUIT, FAKE_TA, cbte, 1L);

        assertContains(xml, "<ar:CbteTipo>11</ar:CbteTipo>");
        assertContains(xml, "<ar:ImpTotal>1000.00</ar:ImpTotal>");
        assertContains(xml, "<ar:ImpIVA>0.00</ar:ImpIVA>");
        assertContains(xml, "<ar:ImpTotConc>0.00</ar:ImpTotConc>");
        assertContains(xml, "<ar:ImpOpEx>0.00</ar:ImpOpEx>");
        // No Iva array for class C
        assertFalse(xml.contains("<ar:Iva>"), "Class C must not include Iva array");
    }

    @Test
    void facturaC_containsCondicionIvaReceptor() {
        Comprobante cbte = facturaC(new BigDecimal("500.00"));
        String xml = FeCaeMapper.buildBody(CUIT, FAKE_TA, cbte, 1L);

        assertContains(xml, "<ar:CondicionIVAReceptorId>5</ar:CondicionIVAReceptorId>");
    }

    @Test
    void facturaC_cbteDesdeEqualsCbteHasta() {
        Comprobante cbte = facturaC(new BigDecimal("200.00"));
        String xml = FeCaeMapper.buildBody(CUIT, FAKE_TA, cbte, 42L);

        assertContains(xml, "<ar:CbteDesde>42</ar:CbteDesde>");
        assertContains(xml, "<ar:CbteHasta>42</ar:CbteHasta>");
    }

    @Test
    void facturaC_rejectsNonEmptyIvaArray() {
        Comprobante cbte = Comprobante.builder()
                .tipo(InvoiceType.FACTURA_C)
                .ptoVta(1).fechaCbte(LocalDate.now())
                .docTipo(IdType.DOC_SIN_NUMERO).docNro("0")
                .condicionIvaReceptor(IvaCondition.CONSUMIDOR_FINAL)
                .concepto(InvoiceConcept.PRODUCTOS)
                .impNeto(new BigDecimal("826.45"))
                .impIva(new BigDecimal("173.55"))  // wrong for C
                .impTotal(new BigDecimal("1000.00"))
                .iva(List.of(new AlicuotaIva(IvaType.IVA_21,
                        new BigDecimal("826.45"), new BigDecimal("173.55"))))
                .build();

        assertThrows(ArcaException.class,
                () -> FeCaeMapper.buildBody(CUIT, FAKE_TA, cbte, 1L));
    }

    // ── Factura A ─────────────────────────────────────────────────────────────

    @Test
    void facturaA_buildsIvaArray() {
        Comprobante cbte = Comprobante.builder()
                .tipo(InvoiceType.FACTURA_A)
                .ptoVta(1).fechaCbte(LocalDate.now())
                .docTipo(IdType.CUIT).docNro("30712345678")
                .condicionIvaReceptor(IvaCondition.IVA_RESPONSABLE_INSCRIPTO)
                .concepto(InvoiceConcept.PRODUCTOS)
                .impNeto(new BigDecimal("826.45"))
                .impIva(new BigDecimal("173.55"))
                .impTotal(new BigDecimal("1000.00"))
                .iva(List.of(new AlicuotaIva(IvaType.IVA_21,
                        new BigDecimal("826.45"), new BigDecimal("173.55"))))
                .build();

        String xml = FeCaeMapper.buildBody(CUIT, FAKE_TA, cbte, 1L);

        assertContains(xml, "<ar:Iva>");
        assertContains(xml, "<ar:Id>5</ar:Id>");  // IVA_21 = code 5
        assertContains(xml, "<ar:BaseImp>826.45</ar:BaseImp>");
        assertContains(xml, "<ar:Importe>173.55</ar:Importe>");
        assertContains(xml, "<ar:DocTipo>80</ar:DocTipo>");  // CUIT
    }

    @Test
    void facturaA_rejectsNonCuitDocTipo() {
        Comprobante cbte = Comprobante.builder()
                .tipo(InvoiceType.FACTURA_A)
                .ptoVta(1).fechaCbte(LocalDate.now())
                .docTipo(IdType.DOC_SIN_NUMERO).docNro("0")  // wrong for A
                .condicionIvaReceptor(IvaCondition.IVA_RESPONSABLE_INSCRIPTO)
                .concepto(InvoiceConcept.PRODUCTOS)
                .impNeto(new BigDecimal("100.00"))
                .impTotal(new BigDecimal("100.00"))
                .build();

        assertThrows(ArcaException.class,
                () -> FeCaeMapper.buildBody(CUIT, FAKE_TA, cbte, 1L));
    }

    // ── Nota de Crédito ───────────────────────────────────────────────────────

    @Test
    void notaCreditoC_includesCbtesAsoc() {
        Comprobante cbte = Comprobante.builder()
                .tipo(InvoiceType.NOTA_CREDITO_C)
                .ptoVta(1).fechaCbte(LocalDate.now())
                .docTipo(IdType.DOC_SIN_NUMERO).docNro("0")
                .condicionIvaReceptor(IvaCondition.CONSUMIDOR_FINAL)
                .concepto(InvoiceConcept.PRODUCTOS)
                .impNeto(new BigDecimal("100.00"))
                .impTotal(new BigDecimal("100.00"))
                .cbtesAsoc(List.of(new CbteAsociado(InvoiceType.FACTURA_C, 1, 5L)))
                .build();

        String xml = FeCaeMapper.buildBody(CUIT, FAKE_TA, cbte, 1L);

        assertContains(xml, "<ar:CbtesAsoc>");
        assertContains(xml, "<ar:Tipo>11</ar:Tipo>");  // FACTURA_C = 11
        assertContains(xml, "<ar:PtoVta>1</ar:PtoVta>");
        assertContains(xml, "<ar:Nro>5</ar:Nro>");
    }

    @Test
    void notaCreditoC_rejectsEmptyCbtesAsoc() {
        Comprobante cbte = Comprobante.builder()
                .tipo(InvoiceType.NOTA_CREDITO_C)
                .ptoVta(1).fechaCbte(LocalDate.now())
                .docTipo(IdType.DOC_SIN_NUMERO).docNro("0")
                .condicionIvaReceptor(IvaCondition.CONSUMIDOR_FINAL)
                .concepto(InvoiceConcept.PRODUCTOS)
                .impNeto(new BigDecimal("100.00"))
                .impTotal(new BigDecimal("100.00"))
                // no cbtesAsoc
                .build();

        assertThrows(ArcaException.class,
                () -> FeCaeMapper.buildBody(CUIT, FAKE_TA, cbte, 1L));
    }

    // ── Service dates ─────────────────────────────────────────────────────────

    @Test
    void servicios_includesServicePeriodFields() {
        Comprobante cbte = Comprobante.builder()
                .tipo(InvoiceType.FACTURA_C)
                .ptoVta(1).fechaCbte(LocalDate.of(2026, 6, 14))
                .docTipo(IdType.DOC_SIN_NUMERO).docNro("0")
                .condicionIvaReceptor(IvaCondition.CONSUMIDOR_FINAL)
                .concepto(InvoiceConcept.SERVICIOS)
                .impNeto(new BigDecimal("1000.00"))
                .impTotal(new BigDecimal("1000.00"))
                .fchServDesde(LocalDate.of(2026, 6, 1))
                .fchServHasta(LocalDate.of(2026, 6, 30))
                .fchVtoPago(LocalDate.of(2026, 7, 10))
                .build();

        String xml = FeCaeMapper.buildBody(CUIT, FAKE_TA, cbte, 1L);

        assertContains(xml, "<ar:FchServDesde>20260601</ar:FchServDesde>");
        assertContains(xml, "<ar:FchServHasta>20260630</ar:FchServHasta>");
        assertContains(xml, "<ar:FchVtoPago>20260710</ar:FchVtoPago>");
    }

    @Test
    void servicios_missingServiceDates_throws() {
        Comprobante cbte = Comprobante.builder()
                .tipo(InvoiceType.FACTURA_C)
                .ptoVta(1).fechaCbte(LocalDate.now())
                .docTipo(IdType.DOC_SIN_NUMERO).docNro("0")
                .condicionIvaReceptor(IvaCondition.CONSUMIDOR_FINAL)
                .concepto(InvoiceConcept.SERVICIOS)  // requires service dates
                .impNeto(new BigDecimal("100.00"))
                .impTotal(new BigDecimal("100.00"))
                // fchServDesde/Hasta/VtoPago intentionally omitted
                .build();

        assertThrows(ArcaException.class,
                () -> FeCaeMapper.buildBody(CUIT, FAKE_TA, cbte, 1L));
    }

    // ── Clase ↔ condición IVA coherence ──────────────────────────────────────

    @Test
    void facturaA_nonRI_condicionThrows() {
        Comprobante cbte = Comprobante.builder()
                .tipo(InvoiceType.FACTURA_A)
                .ptoVta(1).fechaCbte(LocalDate.now())
                .docTipo(IdType.CUIT).docNro("30712345678")
                .condicionIvaReceptor(IvaCondition.CONSUMIDOR_FINAL)  // invalid for A
                .concepto(InvoiceConcept.PRODUCTOS)
                .impNeto(new BigDecimal("100.00"))
                .impTotal(new BigDecimal("100.00"))
                .build();

        assertThrows(ArcaException.class,
                () -> FeCaeMapper.buildBody(CUIT, FAKE_TA, cbte, 1L));
    }

    @Test
    void facturaC_riCondicionThrows() {
        Comprobante cbte = Comprobante.builder()
                .tipo(InvoiceType.FACTURA_C)
                .ptoVta(1).fechaCbte(LocalDate.now())
                .docTipo(IdType.DOC_SIN_NUMERO).docNro("0")
                .condicionIvaReceptor(IvaCondition.IVA_RESPONSABLE_INSCRIPTO)  // invalid for C
                .concepto(InvoiceConcept.PRODUCTOS)
                .impNeto(new BigDecimal("100.00"))
                .impTotal(new BigDecimal("100.00"))
                .build();

        assertThrows(ArcaException.class,
                () -> FeCaeMapper.buildBody(CUIT, FAKE_TA, cbte, 1L));
    }

    // ── IVA aggregation ───────────────────────────────────────────────────────

    @Test
    void facturaB_duplicateIvaRates_areAggregated() {
        // Caller provides two entries with the same rate; mapper must aggregate them
        Comprobante cbte = Comprobante.builder()
                .tipo(InvoiceType.FACTURA_B)
                .ptoVta(1).fechaCbte(LocalDate.now())
                .docTipo(IdType.DOC_SIN_NUMERO).docNro("0")
                .condicionIvaReceptor(IvaCondition.CONSUMIDOR_FINAL)
                .concepto(InvoiceConcept.PRODUCTOS)
                .impNeto(new BigDecimal("1652.89"))
                .impIva(new BigDecimal("347.11"))
                .impTotal(new BigDecimal("2000.00"))
                .iva(List.of(
                        new AlicuotaIva(IvaType.IVA_21, new BigDecimal("826.45"), new BigDecimal("173.55")),
                        new AlicuotaIva(IvaType.IVA_21, new BigDecimal("826.44"), new BigDecimal("173.55"))
                ))
                .build();

        String xml = FeCaeMapper.buildBody(CUIT, FAKE_TA, cbte, 1L);

        // Should appear only once
        int countId = countOccurrences(xml, "<ar:Id>5</ar:Id>");
        assertEquals(1, countId, "Duplicate IVA rate must be aggregated into a single AlicIva entry");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Comprobante facturaC(BigDecimal importe) {
        return Comprobante.builder()
                .tipo(InvoiceType.FACTURA_C)
                .ptoVta(1)
                .fechaCbte(LocalDate.now())
                .docTipo(IdType.DOC_SIN_NUMERO)
                .docNro("0")
                .condicionIvaReceptor(IvaCondition.CONSUMIDOR_FINAL)
                .concepto(InvoiceConcept.PRODUCTOS)
                .impNeto(importe)
                .impTotal(importe)
                .build();
    }

    private static void assertContains(String xml, String fragment) {
        assertTrue(xml.contains(fragment),
                "Expected XML to contain: " + fragment + "\nActual:\n" + xml);
    }

    private static int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) { count++; idx += sub.length(); }
        return count;
    }
}
