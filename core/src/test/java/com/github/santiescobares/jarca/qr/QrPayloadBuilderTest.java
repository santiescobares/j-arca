package com.github.santiescobares.jarca.qr;

import com.github.santiescobares.jarca.error.ArcaObservacion;
import com.github.santiescobares.jarca.model.Cae;
import com.github.santiescobares.jarca.model.Comprobante;
import com.github.santiescobares.jarca.model.ResultadoEmision;
import com.github.santiescobares.jarca.model.enums.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QrPayloadBuilder — verifies URL structure and JSON payload content.
 */
class QrPayloadBuilderTest {

    private static final String CUIT = "20123456789";

    @Test
    void build_urlStartsWithBaseUrl() {
        String url = QrPayloadBuilder.build(CUIT, facturaC(), approvedResultado(1L));
        assertTrue(url.startsWith("https://www.arca.gob.ar/fe/qr/?p="),
                "QR URL must start with the ARCA base URL");
    }

    @Test
    void build_base64DecodeContainsValidJson() {
        ResultadoEmision resultado = approvedResultado(7L);
        String url = QrPayloadBuilder.build(CUIT, facturaC(), resultado);

        String encoded = url.substring("https://www.arca.gob.ar/fe/qr/?p=".length());
        String json = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);

        assertTrue(json.contains("\"ver\":1"),        "JSON must contain ver=1");
        assertTrue(json.contains("\"cuit\":20123456789"), "JSON must contain CUIT as number");
        assertTrue(json.contains("\"tipoCmp\":11"),   "JSON must contain CbteTipo");
        assertTrue(json.contains("\"nroCmp\":7"),     "JSON must contain cbteNro");
        assertTrue(json.contains("\"tipoCodAut\":\"E\""), "tipoCodAut must be E for CAE");
        assertTrue(json.contains("\"codAut\":12345678901234"), "JSON must contain CAE as number");
        assertTrue(json.contains("\"moneda\":\"PES\""), "JSON must contain moneda");
    }

    @Test
    void build_facturaFecha_isFormattedCorrectly() {
        Comprobante cbte = Comprobante.builder()
                .tipo(InvoiceType.FACTURA_C)
                .ptoVta(1)
                .fechaCbte(LocalDate.of(2026, 6, 14))
                .docTipo(IdType.DOC_SIN_NUMERO).docNro("0")
                .condicionIvaReceptor(IvaCondition.CONSUMIDOR_FINAL)
                .concepto(InvoiceConcept.PRODUCTOS)
                .impNeto(new BigDecimal("500.00"))
                .impTotal(new BigDecimal("500.00"))
                .build();

        String url = QrPayloadBuilder.build(CUIT, cbte, approvedResultado(1L));
        String json = decodeJson(url);
        assertTrue(json.contains("\"fecha\":\"2026-06-14\""), "Date must be in yyyy-MM-dd format");
    }

    @Test
    void build_importe_isFormattedWithTwoDecimals() {
        Comprobante cbte = Comprobante.builder()
                .tipo(InvoiceType.FACTURA_C)
                .ptoVta(1).fechaCbte(LocalDate.now())
                .docTipo(IdType.DOC_SIN_NUMERO).docNro("0")
                .condicionIvaReceptor(IvaCondition.CONSUMIDOR_FINAL)
                .concepto(InvoiceConcept.PRODUCTOS)
                .impNeto(new BigDecimal("1234.56"))
                .impTotal(new BigDecimal("1234.56"))
                .build();

        String json = decodeJson(QrPayloadBuilder.build(CUIT, cbte, approvedResultado(1L)));
        assertTrue(json.contains("\"importe\":1234.56"), "Importe must have 2 decimal places");
    }

    @Test
    void build_customBaseUrl_isUsed() {
        String customBase = "https://testing.example.com/qr/?p=";
        String url = QrPayloadBuilder.build(customBase, CUIT, facturaC(), approvedResultado(1L));
        assertTrue(url.startsWith(customBase), "Custom base URL must be used as prefix");
        // Payload must still be valid Base64 JSON
        String json = new String(Base64.getDecoder().decode(
                url.substring(customBase.length())), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"ver\":1"));
    }

    @Test
    void build_throwsWhenNotApproved() {
        ResultadoEmision rejected = new ResultadoEmision(
                InvoiceResult.RECHAZADO, 1L, null,
                List.of(), List.of(new ArcaObservacion(500, "error")));

        assertThrows(IllegalArgumentException.class,
                () -> QrPayloadBuilder.build(CUIT, facturaC(), rejected));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Comprobante facturaC() {
        return Comprobante.builder()
                .tipo(InvoiceType.FACTURA_C)
                .ptoVta(1).fechaCbte(LocalDate.now())
                .docTipo(IdType.DOC_SIN_NUMERO).docNro("0")
                .condicionIvaReceptor(IvaCondition.CONSUMIDOR_FINAL)
                .concepto(InvoiceConcept.PRODUCTOS)
                .impNeto(new BigDecimal("1000.00"))
                .impTotal(new BigDecimal("1000.00"))
                .build();
    }

    private static ResultadoEmision approvedResultado(long cbteNro) {
        Cae cae = new Cae("12345678901234", LocalDate.now().plusDays(10));
        return new ResultadoEmision(InvoiceResult.APROBADO, cbteNro, cae, List.of(), List.of());
    }

    private static String decodeJson(String url) {
        String encoded = url.substring("https://www.arca.gob.ar/fe/qr/?p=".length());
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }
}
