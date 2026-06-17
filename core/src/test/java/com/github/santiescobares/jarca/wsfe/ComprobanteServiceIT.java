package com.github.santiescobares.jarca.wsfe;

import com.github.santiescobares.jarca.auth.WsaaClient;
import com.github.santiescobares.jarca.config.ArcaProperties;
import com.github.santiescobares.jarca.config.Environment;
import com.github.santiescobares.jarca.crypto.BouncyCastleCmsSigner;
import com.github.santiescobares.jarca.crypto.CertificateLoader;
import com.github.santiescobares.jarca.model.AlicuotaIva;
import com.github.santiescobares.jarca.model.CbteAsociado;
import com.github.santiescobares.jarca.model.Comprobante;
import com.github.santiescobares.jarca.model.ResultadoEmision;
import com.github.santiescobares.jarca.model.enums.*;
import com.github.santiescobares.jarca.qr.QrPayloadBuilder;
import com.github.santiescobares.jarca.testsupport.FilePersistentArcaCache;
import com.github.santiescobares.jarca.testsupport.ItCredentials;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for {@link ComprobanteServiceImpl} against the ARCA homologación environment.
 * Covers the §15.1 homologation checklist.
 *
 * <p>Required environment variables:
 * <ul>
 *   <li>{@code ARCA_CERT_PATH} — path to the PEM certificate (.crt)</li>
 *   <li>{@code ARCA_KEY_PATH}  — path to the PEM private key</li>
 *   <li>{@code ARCA_CUIT}      — CUIT associated with the certificate</li>
 * </ul>
 *
 * <p>Optional:
 * <ul>
 *   <li>{@code ARCA_PTO_VTA}   — punto de venta for tests (defaults to 1)</li>
 * </ul>
 *
 * <p>Run with: {@code mvn verify -Phomologacion}
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ComprobanteServiceIT {

    private static ArcaProperties props;
    private static ComprobanteServiceImpl service;
    private static WsfevClient wsfevClient;
    private static WsaaClient wsaaClient;
    private static int ptoVta;

    /** Shared state: Factura C emitted in test 10, referenced by NC/ND and QR tests. */
    private static long       emittedFacturaCNro = -1;
    private static Comprobante emittedFacturaCCbte;

    @BeforeAll
    static void setup() {
        String certPath = ItCredentials.resolve("arca.cert", "ARCA_CERT_PATH");
        String keyPath = ItCredentials.resolve("arca.key", "ARCA_KEY_PATH");
        String cuit = ItCredentials.resolve("arca.cuit", "ARCA_CUIT");

        assumeTrue(certPath != null && keyPath != null && cuit != null,
                "Skipping IT: ARCA_CERT_PATH / ARCA_KEY_PATH / ARCA_CUIT not set");

        ptoVta = Integer.parseInt(ItCredentials.resolve("arca.ptoVta", "ARCA_PTO_VTA", "1"));

        props = ArcaProperties.builder()
                .environment(Environment.HOMOLOGACION)
                .cuit(cuit)
                .certificatePath(certPath)
                .privateKeyPath(keyPath)
                .build();

        CertificateLoader.CertAndKey ck = CertificateLoader.fromProperties(props);
        BouncyCastleCmsSigner signer = new BouncyCastleCmsSigner(ck.certificate(), ck.privateKey());
        FilePersistentArcaCache cache = new FilePersistentArcaCache();
        wsaaClient = new WsaaClient(props, signer, cache);
        wsfevClient = new WsfevClient(props);
        service = new ComprobanteServiceImpl(props, wsaaClient, wsfevClient);
    }

    // ── Factura C ─────────────────────────────────────────────────────────────

    @Test
    @Order(10)
    void emitirFacturaC_returnsCaeWith14Digits() {
        emittedFacturaCCbte = Comprobante.builder()
                .tipo(InvoiceType.FACTURA_C)
                .ptoVta(ptoVta)
                .fechaCbte(LocalDate.now(ArcaProperties.ZONE))
                .docTipo(IdType.DOC_SIN_NUMERO)  // DocTipo 99 = consumidor final
                .docNro("0")
                .condicionIvaReceptor(IvaCondition.CONSUMIDOR_FINAL)
                .concepto(InvoiceConcept.PRODUCTOS)
                .impNeto(new BigDecimal("1000.00"))
                .impTotal(new BigDecimal("1000.00"))
                .build();

        ResultadoEmision r = service.emitir(emittedFacturaCCbte);

        assertTrue(r.isAprobado(), "Factura C must be approved");
        assertNotNull(r.cae(), "Factura C must have a CAE");
        assertEquals(14, r.cae().codigo().length(), "CAE must be exactly 14 digits");
        assertNotNull(r.cae().vencimiento(), "CAE must have a vencimiento date");
        assertTrue(r.cbteNro() > 0, "Factura C must have a positive cbteNro");

        emittedFacturaCNro = r.cbteNro();
        System.out.printf("Factura C emitted: nro=%d CAE=%s vto=%s%n",
                r.cbteNro(), r.cae().codigo(), r.cae().vencimiento());
    }

    // ── QR payload for Factura C ──────────────────────────────────────────────

    @Test
    @Order(11)
    void qrPayload_facturaC_validBase64Url() {
        assumeTrue(emittedFacturaCNro > 0 && emittedFacturaCCbte != null,
                "Factura C not yet emitted; run test 10 first");

        ResultadoEmision fakeResultado = new ResultadoEmision(
                InvoiceResult.APROBADO,
                emittedFacturaCNro,
                new com.github.santiescobares.jarca.model.Cae(
                        "00000000000001", // placeholder — real CAE not stored; just test the builder
                        LocalDate.now(ArcaProperties.ZONE).plusDays(10)),
                List.of(),
                List.of());

        // Build using the real emission result from test 10 is not feasible without storing it,
        // so verify QrPayloadBuilder independently with plausible data.
        String url = QrPayloadBuilder.build(props.getCuit(), emittedFacturaCCbte, fakeResultado);

        assertNotNull(url, "QR URL must not be null");
        assertTrue(url.startsWith("https://www.arca.gob.ar/fe/qr/?p="),
                "QR URL must start with the official ARCA QR base");
        String base64 = url.substring(url.indexOf("?p=") + 3);
        assertDoesNotThrow(
                () -> java.util.Base64.getDecoder().decode(base64),
                "QR parameter must be valid Base64");
        System.out.println("QR URL: " + url);
    }

    // ── Nota de Crédito C ─────────────────────────────────────────────────────

    @Test
    @Order(20)
    void emitirNotaCreditoC_returnsCae() {
        assumeTrue(emittedFacturaCNro > 0, "Factura C not yet emitted; run test 10 first");

        Comprobante nc = Comprobante.builder()
                .tipo(InvoiceType.NOTA_CREDITO_C)
                .ptoVta(ptoVta)
                .fechaCbte(LocalDate.now(ArcaProperties.ZONE))
                .docTipo(IdType.DOC_SIN_NUMERO)
                .docNro("0")
                .condicionIvaReceptor(IvaCondition.CONSUMIDOR_FINAL)
                .concepto(InvoiceConcept.PRODUCTOS)
                .impNeto(new BigDecimal("500.00"))
                .impTotal(new BigDecimal("500.00"))
                .cbtesAsoc(List.of(new CbteAsociado(InvoiceType.FACTURA_C, ptoVta, emittedFacturaCNro)))
                .build();

        ResultadoEmision r = service.emitir(nc);

        assertTrue(r.isAprobado(), "NC C must be approved");
        assertNotNull(r.cae(), "NC C must have a CAE");
        assertEquals(14, r.cae().codigo().length(), "CAE must be exactly 14 digits");
        System.out.printf("NC C emitted: nro=%d CAE=%s%n", r.cbteNro(), r.cae().codigo());
    }

    // ── Nota de Débito C ──────────────────────────────────────────────────────

    @Test
    @Order(21)
    void emitirNotaDebitoC_returnsCae() {
        assumeTrue(emittedFacturaCNro > 0, "Factura C not yet emitted; run test 10 first");

        Comprobante nd = Comprobante.builder()
                .tipo(InvoiceType.NOTA_DEBITO_C)
                .ptoVta(ptoVta)
                .fechaCbte(LocalDate.now(ArcaProperties.ZONE))
                .docTipo(IdType.DOC_SIN_NUMERO)
                .docNro("0")
                .condicionIvaReceptor(IvaCondition.CONSUMIDOR_FINAL)
                .concepto(InvoiceConcept.PRODUCTOS)
                .impNeto(new BigDecimal("100.00"))
                .impTotal(new BigDecimal("100.00"))
                .cbtesAsoc(List.of(new CbteAsociado(InvoiceType.FACTURA_C, ptoVta, emittedFacturaCNro)))
                .build();

        ResultadoEmision r = service.emitir(nd);

        assertTrue(r.isAprobado(), "ND C must be approved");
        assertNotNull(r.cae(), "ND C must have a CAE");
        System.out.printf("ND C emitted: nro=%d CAE=%s%n", r.cbteNro(), r.cae().codigo());
    }

    // ── Factura B (IVA 21 % y 10,5 %) ────────────────────────────────────────

    @Test
    @Order(30)
    void emitirFacturaB_withIva21_returnsCae() {
        Comprobante cbte = Comprobante.builder()
                .tipo(InvoiceType.FACTURA_B)
                .ptoVta(ptoVta)
                .fechaCbte(LocalDate.now(ArcaProperties.ZONE))
                .docTipo(IdType.DOC_SIN_NUMERO)
                .docNro("0")
                .condicionIvaReceptor(IvaCondition.CONSUMIDOR_FINAL)
                .concepto(InvoiceConcept.PRODUCTOS)
                .impNeto(new BigDecimal("1000.00"))
                .impIva(new BigDecimal("210.00"))
                .impTotal(new BigDecimal("1210.00"))
                .iva(List.of(new AlicuotaIva(IvaType.IVA_21,
                        new BigDecimal("1000.00"),
                        new BigDecimal("210.00"))))
                .build();

        ResultadoEmision r = service.emitir(cbte);

        assertTrue(r.isAprobado(), "Factura B with IVA 21% must be approved");
        assertNotNull(r.cae());
        assertEquals(14, r.cae().codigo().length());
        System.out.printf("Factura B (IVA 21%%) emitted: nro=%d CAE=%s%n",
                r.cbteNro(), r.cae().codigo());
    }

    @Test
    @Order(31)
    void emitirFacturaB_withIva105_returnsCae() {
        Comprobante cbte = Comprobante.builder()
                .tipo(InvoiceType.FACTURA_B)
                .ptoVta(ptoVta)
                .fechaCbte(LocalDate.now(ArcaProperties.ZONE))
                .docTipo(IdType.DOC_SIN_NUMERO)
                .docNro("0")
                .condicionIvaReceptor(IvaCondition.CONSUMIDOR_FINAL)
                .concepto(InvoiceConcept.PRODUCTOS)
                .impNeto(new BigDecimal("2000.00"))
                .impIva(new BigDecimal("210.00"))
                .impTotal(new BigDecimal("2210.00"))
                .iva(List.of(new AlicuotaIva(IvaType.IVA_10_5,
                        new BigDecimal("2000.00"),
                        new BigDecimal("210.00"))))
                .build();

        ResultadoEmision r = service.emitir(cbte);

        assertTrue(r.isAprobado(), "Factura B with IVA 10.5% must be approved");
        assertNotNull(r.cae());
        System.out.printf("Factura B (IVA 10.5%%) emitted: nro=%d CAE=%s%n",
                r.cbteNro(), r.cae().codigo());
    }

    // ── Idempotencia: FECompConsultar tras error de transporte ───────────────

    @Test
    @Order(40)
    void feCompConsultar_idempotency_existingComprobante() {
        assumeTrue(emittedFacturaCNro > 0, "Factura C not yet emitted; run test 10 first");

        var found = service.consultar(ptoVta, InvoiceType.FACTURA_C.getCodigo(), emittedFacturaCNro);

        assertTrue(found.isPresent(),
                "FECompConsultar must find the already-emitted Factura C");
        assertNotNull(found.get().cae(),
                "FECompConsultar result must include the CAE");
        System.out.printf("Idempotency check: found nro=%d CAE=%s%n",
                found.get().cbteNro(), found.get().cae().codigo());
    }
}
