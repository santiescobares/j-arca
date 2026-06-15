package com.github.santiescobares.jarca.wsfe;

import com.github.santiescobares.jarca.auth.TicketAccess;
import com.github.santiescobares.jarca.auth.WsaaClient;
import com.github.santiescobares.jarca.cache.InMemoryArcaCache;
import com.github.santiescobares.jarca.config.ArcaProperties;
import com.github.santiescobares.jarca.config.Environment;
import com.github.santiescobares.jarca.crypto.CmsSigner;
import com.github.santiescobares.jarca.error.ArcaObservacion;
import com.github.santiescobares.jarca.error.ArcaRechazo;
import com.github.santiescobares.jarca.model.Comprobante;
import com.github.santiescobares.jarca.model.ResultadoEmision;
import com.github.santiescobares.jarca.model.enums.*;
import com.github.santiescobares.jarca.soap.SoapClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ComprobanteServiceImpl using a stub HTTP server.
 * Verifies orchestration: TA acquisition, number assignment, CAE parsing, and error-10016 recovery.
 */
class ComprobanteServiceImplTest {

    private static HttpServer stubServer;
    private static int port;
    private static final AtomicReference<String> wsaaResponse = new AtomicReference<>();
    private static final AtomicReference<String> wsfeResponse = new AtomicReference<>();
    private static final AtomicInteger wsfeHitCount = new AtomicInteger(0);

    private static final CmsSigner NOOP_SIGNER = data -> new byte[]{0x01};

    @BeforeAll
    static void startStub() throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress(0), 0);
        port = stubServer.getAddress().getPort();

        stubServer.createContext("/wsaa", exchange -> {
            String resp = wsaaResponse.get() != null ? wsaaResponse.get() : buildFakeWsaaResponse();
            byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/xml; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        });

        stubServer.createContext("/wsfe", exchange -> {
            wsfeHitCount.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String resp = wsfeResponse.get() != null ? wsfeResponse.get() : "";
            byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/xml; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        });

        stubServer.start();
    }

    @AfterAll
    static void stopStub() { stubServer.stop(0); }

    @BeforeEach
    void reset() {
        wsaaResponse.set(null);
        wsfeResponse.set(null);
        wsfeHitCount.set(0);
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    void emitir_facturaC_returnsAprobado() {
        // WSFE: first call = FECompUltimoAutorizado (returns 0), second = FECAESolicitar
        wsfeResponse.set(null); // handled below via counter
        setupTwoPhaseWsfe(
                ultimoAutorizadoResponse(0),
                approvedCaeResponse("12345678901234", "20260624")
        );

        ComprobanteServiceImpl svc = buildService();
        ResultadoEmision resultado = svc.emitir(facturaC());

        assertTrue(resultado.isAprobado());
        assertEquals(InvoiceResult.APROBADO, resultado.resultado());
        assertEquals(1L, resultado.cbteNro());
        assertNotNull(resultado.cae());
        assertEquals("12345678901234", resultado.cae().codigo());
    }

    @Test
    void emitir_facturaC_error10016_retriesWithCorrectNumber() {
        // First FECAESolicitar → 10016; re-read last authorised → 4; second attempt → approved with nro 5
        setupThreePhaseWsfe(
                ultimoAutorizadoResponse(3),          // first read: last = 3
                rejectedCaeResponse(10016),           // attempt with nro=4 fails (10016)
                ultimoAutorizadoResponse(4),          // re-read: last = 4
                approvedCaeResponse("12345678901234", "20260624") // attempt with nro=5 succeeds
        );

        ComprobanteServiceImpl svc = buildService();
        ResultadoEmision resultado = svc.emitir(facturaC());

        assertTrue(resultado.isAprobado());
    }

    @Test
    void emitir_facturaC_error10016_inObs_retriesWithCorrectNumber() {
        // 10016 arrives in FECAEDetResponse/Observaciones/Obs (not in the global Errors block)
        setupThreePhaseWsfe(
                ultimoAutorizadoResponse(2),
                rejectedCaeResponseWith10016InObs(),
                ultimoAutorizadoResponse(3),
                approvedCaeResponse("12345678901234", "20260624")
        );

        ResultadoEmision resultado = buildService().emitir(facturaC());
        assertTrue(resultado.isAprobado());
    }

    @Test
    void emitir_facturaC_rejection_throwsArcaRechazo() {
        setupTwoPhaseWsfe(
                ultimoAutorizadoResponse(0),
                rejectedCaeResponse(500)
        );

        ComprobanteServiceImpl svc = buildService();
        ArcaRechazo ex = assertThrows(ArcaRechazo.class, () -> svc.emitir(facturaC()));
        assertTrue(ex.tieneError(500));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ComprobanteServiceImpl buildService() {
        var urls = com.github.santiescobares.jarca.config.ServiceUrls.builder(Environment.HOMOLOGACION)
                .wsaaUrl("http://localhost:" + port + "/wsaa")
                .wsfeUrl("http://localhost:" + port + "/wsfe")
                .build();
        ArcaProperties props = ArcaProperties.builder()
                .environment(Environment.HOMOLOGACION)
                .cuit("20123456789")
                .serviceUrls(urls)
                .build();

        WsaaClient wsaaClient = new WsaaClient(props, NOOP_SIGNER, new InMemoryArcaCache());
        WsfevClient wsfevClient = new WsfevClient(props);
        return new ComprobanteServiceImpl(props, wsaaClient, wsfevClient);
    }

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

    // ── stub response builders ────────────────────────────────────────────────

    private void setupTwoPhaseWsfe(String phase1, String phase2) {
        AtomicInteger counter = new AtomicInteger(0);
        // We override the handler to return different responses
        // Since HttpServer context is already registered, we use the AtomicReference trick
        // but with a sequential list:
        String[] responses = {phase1, phase2};
        wsfeResponse.set(null);
        // Replace handler by swapping responses on each hit
        stubServer.removeContext("/wsfe");
        stubServer.createContext("/wsfe", exchange -> {
            int hit = wsfeHitCount.getAndIncrement();
            String resp = hit < responses.length ? responses[hit] : responses[responses.length - 1];
            byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/xml; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        });
    }

    private void setupThreePhaseWsfe(String... responses) {
        stubServer.removeContext("/wsfe");
        stubServer.createContext("/wsfe", exchange -> {
            int hit = wsfeHitCount.getAndIncrement();
            String resp = hit < responses.length ? responses[hit] : responses[responses.length - 1];
            byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/xml; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        });
    }

    private static String soapEnvelope(String body) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body>%s</soapenv:Body>
                </soapenv:Envelope>
                """.formatted(body);
    }

    private static String ultimoAutorizadoResponse(long nro) {
        return soapEnvelope("""
                <FECompUltimoAutorizadoResponse xmlns="http://ar.gov.afip.dif.FEV1/">
                  <FECompUltimoAutorizadoResult>
                    <CbteNro>%d</CbteNro><PtoVta>1</PtoVta><CbteTipo>11</CbteTipo>
                  </FECompUltimoAutorizadoResult>
                </FECompUltimoAutorizadoResponse>
                """.formatted(nro));
    }

    private static String approvedCaeResponse(String cae, String fchVto) {
        return soapEnvelope("""
                <FECAESolicitarResponse xmlns="http://ar.gov.afip.dif.FEV1/">
                  <FECAESolicitarResult>
                    <FeCabResp><Resultado>A</Resultado></FeCabResp>
                    <FeDetResp>
                      <FECAEDetResponse>
                        <CbteDesde>1</CbteDesde><CbteHasta>1</CbteHasta>
                        <Resultado>A</Resultado>
                        <CAE>%s</CAE><CAEFchVto>%s</CAEFchVto>
                      </FECAEDetResponse>
                    </FeDetResp>
                  </FECAESolicitarResult>
                </FECAESolicitarResponse>
                """.formatted(cae, fchVto));
    }

    private static String rejectedCaeResponse(int errorCode) {
        return soapEnvelope("""
                <FECAESolicitarResponse xmlns="http://ar.gov.afip.dif.FEV1/">
                  <FECAESolicitarResult>
                    <FeCabResp><Resultado>R</Resultado></FeCabResp>
                    <FeDetResp>
                      <FECAEDetResponse>
                        <CbteDesde>1</CbteDesde><Resultado>R</Resultado>
                      </FECAEDetResponse>
                    </FeDetResp>
                    <Errors>
                      <Err><Code>%d</Code><Msg>Error de prueba</Msg></Err>
                    </Errors>
                  </FECAESolicitarResult>
                </FECAESolicitarResponse>
                """.formatted(errorCode));
    }

    private static String rejectedCaeResponseWith10016InObs() {
        return soapEnvelope("""
                <FECAESolicitarResponse xmlns="http://ar.gov.afip.dif.FEV1/">
                  <FECAESolicitarResult>
                    <FeCabResp><Resultado>R</Resultado></FeCabResp>
                    <FeDetResp>
                      <FECAEDetResponse>
                        <CbteDesde>1</CbteDesde><Resultado>R</Resultado>
                        <Observaciones>
                          <Obs><Code>10016</Code><Msg>El numero de comprobante no es el que corresponde</Msg></Obs>
                        </Observaciones>
                      </FECAEDetResponse>
                    </FeDetResp>
                  </FECAESolicitarResult>
                </FECAESolicitarResponse>
                """);
    }

    private static String buildFakeWsaaResponse() {
        String expiry = java.time.OffsetDateTime.now(java.time.ZoneOffset.ofHours(-3))
                .plusHours(12)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));
        String taXml = "<?xml version=\"1.0\"?><loginTicketResponse><header>" +
                "<expirationTime>" + expiry + "</expirationTime></header>" +
                "<credentials><token>TK</token><sign>SG</sign></credentials>" +
                "</loginTicketResponse>";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
               "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
               "<soapenv:Body><loginCmsResponse xmlns=\"http://wsaa.view.sua.dvadac.desein.afip.gov.ar\">" +
               "<loginCmsReturn>" + taXml.replace("<", "&lt;").replace(">", "&gt;") + "</loginCmsReturn>" +
               "</loginCmsResponse></soapenv:Body></soapenv:Envelope>";
    }
}
