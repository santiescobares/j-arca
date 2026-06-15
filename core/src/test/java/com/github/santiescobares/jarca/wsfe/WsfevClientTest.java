package com.github.santiescobares.jarca.wsfe;

import com.github.santiescobares.jarca.auth.TicketAccess;
import com.github.santiescobares.jarca.config.ArcaProperties;
import com.github.santiescobares.jarca.config.Environment;
import com.github.santiescobares.jarca.config.ServiceUrls;
import com.github.santiescobares.jarca.model.enums.InvoiceResult;
import com.github.santiescobares.jarca.soap.SoapClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WsfevClient using a stub HTTP server.
 * No real ARCA credentials needed.
 */
class WsfevClientTest {

    private static HttpServer stubServer;
    private static int port;
    private static final AtomicReference<String> stubResponse = new AtomicReference<>();
    private static final AtomicReference<String> stubPath = new AtomicReference<>("/wsfev1/service.asmx");

    private static final TicketAccess FAKE_TA =
            new TicketAccess("TOKEN_TEST", "SIGN_TEST", Instant.now().plusSeconds(3600));

    @BeforeAll
    static void startStub() throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress(0), 0);
        port = stubServer.getAddress().getPort();

        stubServer.createContext("/", exchange -> {
            String resp = stubResponse.get();
            if (resp == null) resp = "";
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

    private ArcaProperties propsWithStub() {
        ServiceUrls urls = ServiceUrls.builder(Environment.HOMOLOGACION)
                .wsfeUrl("http://localhost:" + port + "/wsfev1/service.asmx")
                .build();
        return ArcaProperties.builder()
                .environment(Environment.HOMOLOGACION)
                .cuit("20123456789")
                .serviceUrls(urls)
                .build();
    }

    // ── FEDummy ──────────────────────────────────────────────────────────────

    @Test
    void feDummy_returnsTrueWhenAllServersOk() {
        stubResponse.set(soapEnvelope("""
                <FEDummyResponse xmlns="http://ar.gov.afip.dif.FEV1/">
                  <FEDummyResult>
                    <AppServer>OK</AppServer>
                    <AuthServer>OK</AuthServer>
                    <DbServer>OK</DbServer>
                  </FEDummyResult>
                </FEDummyResponse>
                """));

        WsfevClient client = new WsfevClient(propsWithStub());
        assertTrue(client.feDummy());
    }

    @Test
    void feDummy_returnsFalseWhenDbServerDown() {
        stubResponse.set(soapEnvelope("""
                <FEDummyResponse xmlns="http://ar.gov.afip.dif.FEV1/">
                  <FEDummyResult>
                    <AppServer>OK</AppServer>
                    <AuthServer>OK</AuthServer>
                    <DbServer>ERROR</DbServer>
                  </FEDummyResult>
                </FEDummyResponse>
                """));

        WsfevClient client = new WsfevClient(propsWithStub());
        assertFalse(client.feDummy());
    }

    // ── FECompUltimoAutorizado ────────────────────────────────────────────────

    @Test
    void feCompUltimoAutorizado_returnsCbteNro() {
        stubResponse.set(soapEnvelope("""
                <FECompUltimoAutorizadoResponse xmlns="http://ar.gov.afip.dif.FEV1/">
                  <FECompUltimoAutorizadoResult>
                    <CbteNro>7</CbteNro>
                    <PtoVta>1</PtoVta>
                    <CbteTipo>11</CbteTipo>
                  </FECompUltimoAutorizadoResult>
                </FECompUltimoAutorizadoResponse>
                """));

        WsfevClient client = new WsfevClient(propsWithStub());
        assertEquals(7L, client.feCompUltimoAutorizado(FAKE_TA, 1, 11));
    }

    @Test
    void feCompUltimoAutorizado_returnsZeroWhenNoneIssued() {
        stubResponse.set(soapEnvelope("""
                <FECompUltimoAutorizadoResponse xmlns="http://ar.gov.afip.dif.FEV1/">
                  <FECompUltimoAutorizadoResult>
                    <CbteNro>0</CbteNro>
                    <PtoVta>1</PtoVta>
                    <CbteTipo>11</CbteTipo>
                  </FECompUltimoAutorizadoResult>
                </FECompUltimoAutorizadoResponse>
                """));

        WsfevClient client = new WsfevClient(propsWithStub());
        assertEquals(0L, client.feCompUltimoAutorizado(FAKE_TA, 1, 11));
    }

    // ── FECAESolicitar ────────────────────────────────────────────────────────

    @Test
    void feCaeSolicitar_approved_parsesCaeAndResult() {
        stubResponse.set(soapEnvelope(approvedCaeResponse("12345678901234", "20260624")));

        WsfevClient client = new WsfevClient(propsWithStub());
        String body = "<ar:FECAESolicitar xmlns:ar=\"http://ar.gov.afip.dif.FEV1/\"/>";
        WsfevClient.CaeResponse resp = client.feCaeSolicitar(FAKE_TA, body);

        assertEquals(InvoiceResult.APROBADO, resp.resultado());
        assertEquals(1L, resp.cbteNro());
        assertEquals("12345678901234", resp.cae());
        assertNotNull(resp.caeFchVto());
        assertTrue(resp.obs().isEmpty());
        assertTrue(resp.errores().isEmpty());
    }

    @Test
    void feCaeSolicitar_rejected_returnsRechazado() {
        stubResponse.set(soapEnvelope("""
                <FECAESolicitarResponse xmlns="http://ar.gov.afip.dif.FEV1/">
                  <FECAESolicitarResult>
                    <FeCabResp><Resultado>R</Resultado></FeCabResp>
                    <FeDetResp>
                      <FECAEDetResponse>
                        <CbteDesde>1</CbteDesde>
                        <Resultado>R</Resultado>
                      </FECAEDetResponse>
                    </FeDetResp>
                    <Errors>
                      <Err><Code>10016</Code><Msg>Numero incorrecto</Msg></Err>
                    </Errors>
                  </FECAESolicitarResult>
                </FECAESolicitarResponse>
                """));

        WsfevClient client = new WsfevClient(propsWithStub());
        WsfevClient.CaeResponse resp = client.feCaeSolicitar(FAKE_TA,
                "<ar:FECAESolicitar xmlns:ar=\"http://ar.gov.afip.dif.FEV1/\"/>");

        assertEquals(InvoiceResult.RECHAZADO, resp.resultado());
        assertNull(resp.cae());
        assertFalse(resp.errores().isEmpty());
        assertEquals(10016, resp.errores().get(0).codigo());
    }

    @Test
    void feCaeSolicitar_approvedWithObs_returnsAprobadoConObservaciones() {
        stubResponse.set(soapEnvelope("""
                <FECAESolicitarResponse xmlns="http://ar.gov.afip.dif.FEV1/">
                  <FECAESolicitarResult>
                    <FeCabResp><Resultado>A</Resultado></FeCabResp>
                    <FeDetResp>
                      <FECAEDetResponse>
                        <CbteDesde>1</CbteDesde>
                        <Resultado>A</Resultado>
                        <CAE>12345678901234</CAE>
                        <CAEFchVto>20260624</CAEFchVto>
                        <Observaciones>
                          <Obs><Code>500</Code><Msg>Observacion de prueba</Msg></Obs>
                        </Observaciones>
                      </FECAEDetResponse>
                    </FeDetResp>
                  </FECAESolicitarResult>
                </FECAESolicitarResponse>
                """));

        WsfevClient client = new WsfevClient(propsWithStub());
        WsfevClient.CaeResponse resp = client.feCaeSolicitar(FAKE_TA,
                "<ar:FECAESolicitar xmlns:ar=\"http://ar.gov.afip.dif.FEV1/\"/>");

        assertEquals(InvoiceResult.APROBADO_CON_OBSERVACIONES, resp.resultado());
        assertEquals("12345678901234", resp.cae());
        assertEquals(1, resp.obs().size());
        assertEquals(500, resp.obs().get(0).codigo());
    }

    // ── FEParamGetTiposCbte ───────────────────────────────────────────────────

    @Test
    void feParamGetTiposCbte_returnsCodes() {
        stubResponse.set(soapEnvelope("""
                <FEParamGetTiposCbteResponse xmlns="http://ar.gov.afip.dif.FEV1/">
                  <FEParamGetTiposCbteResult>
                    <ResultGet>
                      <CbteTipo><Id>1</Id><Desc>Facturas A</Desc></CbteTipo>
                      <CbteTipo><Id>6</Id><Desc>Facturas B</Desc></CbteTipo>
                      <CbteTipo><Id>11</Id><Desc>Facturas C</Desc></CbteTipo>
                    </ResultGet>
                  </FEParamGetTiposCbteResult>
                </FEParamGetTiposCbteResponse>
                """));

        WsfevClient client = new WsfevClient(propsWithStub());
        var tipos = client.feParamGetTiposCbte(FAKE_TA);

        assertTrue(tipos.contains(1));
        assertTrue(tipos.contains(6));
        assertTrue(tipos.contains(11));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String approvedCaeResponse(String cae, String fchVto) {
        return """
                <FECAESolicitarResponse xmlns="http://ar.gov.afip.dif.FEV1/">
                  <FECAESolicitarResult>
                    <FeCabResp><Resultado>A</Resultado></FeCabResp>
                    <FeDetResp>
                      <FECAEDetResponse>
                        <CbteDesde>1</CbteDesde>
                        <CbteHasta>1</CbteHasta>
                        <Resultado>A</Resultado>
                        <CAE>%s</CAE>
                        <CAEFchVto>%s</CAEFchVto>
                      </FECAEDetResponse>
                    </FeDetResp>
                  </FECAESolicitarResult>
                </FECAESolicitarResponse>
                """.formatted(cae, fchVto);
    }

    private static String soapEnvelope(String body) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body>
                    %s
                  </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(body);
    }
}
