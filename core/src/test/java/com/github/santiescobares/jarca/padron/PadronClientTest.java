package com.github.santiescobares.jarca.padron;

import com.github.santiescobares.jarca.auth.TicketAccess;
import com.github.santiescobares.jarca.config.ArcaProperties;
import com.github.santiescobares.jarca.config.Environment;
import com.github.santiescobares.jarca.config.ServiceUrls;
import com.github.santiescobares.jarca.error.ArcaException;
import com.github.santiescobares.jarca.model.enums.IvaCondition;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PadronClient and CondicionIvaResolver using a stub HTTP server.
 */
class PadronClientTest {

    private static HttpServer stubServer;
    private static int port;
    private static final AtomicReference<String> stubResponse = new AtomicReference<>();

    private static final TicketAccess FAKE_TA =
            new TicketAccess("TK", "SG", Instant.now().plusSeconds(3600));

    @BeforeAll
    static void startStub() throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress(0), 0);
        port = stubServer.getAddress().getPort();
        stubServer.createContext("/padron", exchange -> {
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

    private PadronClient buildClient() {
        ServiceUrls urls = ServiceUrls.builder(Environment.HOMOLOGACION)
                .padronUrl("http://localhost:" + port + "/padron")
                .build();
        ArcaProperties props = ArcaProperties.builder()
                .environment(Environment.HOMOLOGACION)
                .cuit("20123456789")
                .serviceUrls(urls)
                .build();
        return new PadronClient(props);
    }

    // ── getPersona ────────────────────────────────────────────────────────────

    @Test
    void getPersona_monotributista_parsesCorrectly() {
        stubResponse.set(soapEnvelope(monotributistaResponse("20999999993", "GARCIA JUAN", "B")));

        PadronClient client = buildClient();
        PadronClient.PersonaData data = client.getPersona(FAKE_TA, "20999999993");

        assertEquals("20999999993", data.cuit());
        assertEquals("B", data.categoriaMonotributo());
        assertFalse(data.responsableInscripto());
        assertFalse(data.exento());
    }

    @Test
    void getPersona_responsableInscripto_parsesCorrectly() {
        stubResponse.set(soapEnvelope(responsableInscriptoResponse("30999999994", "MI EMPRESA SRL")));

        PadronClient.PersonaData data = buildClient().getPersona(FAKE_TA, "30999999994");

        assertNull(data.categoriaMonotributo());
        assertTrue(data.responsableInscripto());
        assertFalse(data.exento());
    }

    @Test
    void getPersona_consumidorFinal_hasNoCategoryAndNotRI() {
        stubResponse.set(soapEnvelope(consumidorFinalResponse("20888888883")));

        PadronClient.PersonaData data = buildClient().getPersona(FAKE_TA, "20888888883");

        assertNull(data.categoriaMonotributo());
        assertFalse(data.responsableInscripto());
        assertFalse(data.exento());
    }

    @Test
    void getPersona_inactiveClaveThrows() {
        stubResponse.set(soapEnvelope(inactivePersonaResponse("20777777772")));

        assertThrows(ArcaException.class,
                () -> buildClient().getPersona(FAKE_TA, "20777777772"));
    }

    // ── CondicionIvaResolver ──────────────────────────────────────────────────

    @Test
    void resolver_monotributo_returnsResponsableMonotributo() {
        var persona = new PadronClient.PersonaData("1", "Test", "B", false, false);
        assertEquals(IvaCondition.RESPONSABLE_MONOTRIBUTO, CondicionIvaResolver.resolve(persona));
    }

    @Test
    void resolver_responsableInscripto_returnsIvaRI() {
        var persona = new PadronClient.PersonaData("1", "Test", null, true, false);
        assertEquals(IvaCondition.IVA_RESPONSABLE_INSCRIPTO, CondicionIvaResolver.resolve(persona));
    }

    @Test
    void resolver_exento_returnsIvaExento() {
        var persona = new PadronClient.PersonaData("1", "Test", null, false, true);
        assertEquals(IvaCondition.IVA_EXENTO, CondicionIvaResolver.resolve(persona));
    }

    @Test
    void resolver_noMatch_returnsConsumidorFinal() {
        var persona = new PadronClient.PersonaData("1", "Test", null, false, false);
        assertEquals(IvaCondition.CONSUMIDOR_FINAL, CondicionIvaResolver.resolve(persona));
    }

    @Test
    void resolver_monotributoTakesPrecedenceOverRI() {
        // Edge case: both flags set (shouldn't happen in practice)
        var persona = new PadronClient.PersonaData("1", "Test", "A", true, false);
        assertEquals(IvaCondition.RESPONSABLE_MONOTRIBUTO, CondicionIvaResolver.resolve(persona));
    }

    // ── stub response builders ────────────────────────────────────────────────

    private static String soapEnvelope(String body) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body>%s</soapenv:Body>
                </soapenv:Envelope>
                """.formatted(body);
    }

    private static String monotributistaResponse(String cuit, String apellidoNombre, String categoria) {
        String[] partes = apellidoNombre.split(" ", 2);
        String apellido = partes[0];
        String nombre   = partes.length > 1 ? partes[1] : "";
        return """
                <getPersona_v2Response xmlns="http://a5.soap.ws.server.puc.sr/">
                  <personaReturn>
                    <datosGenerales>
                      <idPersona>%s</idPersona>
                      <tipoPersona>FISICA</tipoPersona>
                      <estadoClave>ACTIVO</estadoClave>
                      <apellido>%s</apellido>
                      <nombre>%s</nombre>
                    </datosGenerales>
                    <datosMonotributo>
                      <categorias>
                        <categoria>
                          <idCategoria>2</idCategoria>
                          <descripcionCategoria>%s</descripcionCategoria>
                        </categoria>
                      </categorias>
                    </datosMonotributo>
                  </personaReturn>
                </getPersona_v2Response>
                """.formatted(cuit, apellido, nombre, categoria);
    }

    private static String responsableInscriptoResponse(String cuit, String razonSocial) {
        return """
                <getPersona_v2Response xmlns="http://a5.soap.ws.server.puc.sr/">
                  <personaReturn>
                    <datosGenerales>
                      <idPersona>%s</idPersona>
                      <tipoPersona>JURIDICA</tipoPersona>
                      <estadoClave>ACTIVO</estadoClave>
                      <razonSocial>%s</razonSocial>
                    </datosGenerales>
                    <datosRegimenGeneral>
                      <impuesto>
                        <idImpuesto>30</idImpuesto>
                        <descripcionImpuesto>IVA</descripcionImpuesto>
                        <estadoImpuesto>AC</estadoImpuesto>
                      </impuesto>
                    </datosRegimenGeneral>
                  </personaReturn>
                </getPersona_v2Response>
                """.formatted(cuit, razonSocial);
    }

    private static String consumidorFinalResponse(String cuit) {
        return """
                <getPersona_v2Response xmlns="http://a5.soap.ws.server.puc.sr/">
                  <personaReturn>
                    <datosGenerales>
                      <idPersona>%s</idPersona>
                      <tipoPersona>FISICA</tipoPersona>
                      <estadoClave>ACTIVO</estadoClave>
                      <apellido>PEREZ</apellido>
                      <nombre>CARLOS</nombre>
                    </datosGenerales>
                  </personaReturn>
                </getPersona_v2Response>
                """.formatted(cuit);
    }

    private static String inactivePersonaResponse(String cuit) {
        return """
                <getPersona_v2Response xmlns="http://a5.soap.ws.server.puc.sr/">
                  <personaReturn>
                    <datosGenerales>
                      <idPersona>%s</idPersona>
                      <tipoPersona>FISICA</tipoPersona>
                      <estadoClave>INACTIVO</estadoClave>
                      <apellido>LOPEZ</apellido>
                    </datosGenerales>
                  </personaReturn>
                </getPersona_v2Response>
                """.formatted(cuit);
    }
}
