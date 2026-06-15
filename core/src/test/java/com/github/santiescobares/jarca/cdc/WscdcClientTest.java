package com.github.santiescobares.jarca.cdc;

import com.github.santiescobares.jarca.auth.TicketAccess;
import com.github.santiescobares.jarca.config.ArcaProperties;
import com.github.santiescobares.jarca.config.Environment;
import com.github.santiescobares.jarca.config.ServiceUrls;
import com.github.santiescobares.jarca.model.enums.Currency;
import com.github.santiescobares.jarca.model.enums.InvoiceResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WscdcClient using a stub HTTP server.
 */
class WscdcClientTest {

    private static HttpServer stubServer;
    private static int port;
    private static final AtomicReference<String> stubResponse = new AtomicReference<>();

    private static final TicketAccess FAKE_TA =
            new TicketAccess("TK", "SG", Instant.now().plusSeconds(3600));

    @BeforeAll
    static void startStub() throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress(0), 0);
        port = stubServer.getAddress().getPort();
        stubServer.createContext("/wscdc", exchange -> {
            String resp = stubResponse.get() != null ? stubResponse.get() : "";
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

    private WscdcClient buildClient() {
        ServiceUrls urls = ServiceUrls.builder(Environment.HOMOLOGACION)
                .wscdcUrl("http://localhost:" + port + "/wscdc")
                .build();
        ArcaProperties props = ArcaProperties.builder()
                .environment(Environment.HOMOLOGACION)
                .cuit("20123456789")
                .serviceUrls(urls)
                .build();
        return new WscdcClient(props);
    }

    private WscdcClient.ConstatarRequest buildRequest() {
        return new WscdcClient.ConstatarRequest(
                "20999999993",   // cuitEmisor
                11,              // cbteTipo: Factura C
                1,               // ptoVta
                42L,             // nroCbte
                "12345678901234",// cae
                LocalDate.of(2026, 6, 14),
                new BigDecimal("1000.00"),
                Currency.PESOS,
                96,              // docTipoReceptor: DNI
                12345678L        // docNroReceptor
        );
    }

    @Test
    void constatar_approved_returnsAprobado() {
        stubResponse.set(soapEnvelope("<Resultado>A</Resultado>"));

        InvoiceResult result = buildClient().constatar(FAKE_TA, buildRequest());
        assertEquals(InvoiceResult.APROBADO, result);
    }

    @Test
    void constatar_rejected_returnsRechazado() {
        stubResponse.set(soapEnvelope("<Resultado>R</Resultado>"));

        InvoiceResult result = buildClient().constatar(FAKE_TA, buildRequest());
        assertEquals(InvoiceResult.RECHAZADO, result);
    }

    @Test
    void constatar_includesRequiredFieldsInRequest() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();

        stubServer.removeContext("/wscdc");
        stubServer.createContext("/wscdc", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String resp = soapEnvelope("<Resultado>A</Resultado>");
            byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/xml; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        });

        buildClient().constatar(FAKE_TA, buildRequest());

        String body = capturedBody.get();
        assertNotNull(body, "Request body should have been captured");
        assertTrue(body.contains("<wscdc:CuitEmisor>20999999993</wscdc:CuitEmisor>"), "Must include CuitEmisor");
        assertTrue(body.contains("<wscdc:CbteTipo>11</wscdc:CbteTipo>"), "Must include CbteTipo");
        assertTrue(body.contains("<wscdc:CbteNro>42</wscdc:CbteNro>"), "Must include CbteNro");
        assertTrue(body.contains("<wscdc:CbteModo>CAE</wscdc:CbteModo>"), "CbteModo must be CAE");
        assertTrue(body.contains("<wscdc:CodAutorizacion>12345678901234</wscdc:CodAutorizacion>"), "Must include CodAutorizacion");
        assertTrue(body.contains("<wscdc:DocTipoReceptor>96</wscdc:DocTipoReceptor>"), "Must include DocTipoReceptor");
        assertTrue(body.contains("<wscdc:DocNroReceptor>12345678</wscdc:DocNroReceptor>"), "Must include DocNroReceptor");
        assertTrue(body.contains("<wscdc:CbteFch>20260614</wscdc:CbteFch>"), "CbteFch must be yyyyMMdd");
        assertTrue(body.contains("<wscdc:MonId>PES</wscdc:MonId>"), "Must include MonId");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String soapEnvelope(String body) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body>
                    <ComprobanteConstatarResponse xmlns="http://ar.gov.afip.dif.wscdc.service/">
                      <ComprobanteConstatarResult>%s</ComprobanteConstatarResult>
                    </ComprobanteConstatarResponse>
                  </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(body);
    }
}
