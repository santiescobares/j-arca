package com.github.santiescobares.jarca.auth;

import com.github.santiescobares.jarca.cache.ArcaCache;
import com.github.santiescobares.jarca.cache.InMemoryArcaCache;
import com.github.santiescobares.jarca.config.ArcaProperties;
import com.github.santiescobares.jarca.config.Environment;
import com.github.santiescobares.jarca.config.ServiceUrls;
import com.github.santiescobares.jarca.crypto.CmsSigner;
import com.github.santiescobares.jarca.soap.SoapClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WsaaClient using a stub HTTP server (com.sun.net.httpserver).
 * No real ARCA credentials needed.
 */
class WsaaClientTest {

    private static HttpServer stubServer;
    private static int port;
    private static final AtomicInteger hitCount = new AtomicInteger(0);

    private static final CmsSigner NOOP_SIGNER = data -> new byte[]{0x01, 0x02};

    @BeforeAll
    static void startStub() throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress(0), 0);
        port = stubServer.getAddress().getPort();

        stubServer.createContext("/ws/services/LoginCms", exchange -> {
            hitCount.incrementAndGet();
            String ta = buildFakeTaResponse();
            byte[] bytes = ta.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/xml; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        });

        stubServer.start();
    }

    @AfterAll
    static void stopStub() {
        stubServer.stop(0);
    }

    @BeforeEach
    void resetHitCount() {
        hitCount.set(0);
    }

    private ArcaProperties propsWithStub() {
        ServiceUrls urls = ServiceUrls.builder(Environment.HOMOLOGACION)
                .wsaaUrl("http://localhost:" + port + "/ws/services/LoginCms")
                .build();
        return ArcaProperties.builder()
                .environment(Environment.HOMOLOGACION)
                .cuit("20123456789")
                .serviceUrls(urls)
                .build();
    }

    @Test
    void obtener_returnsValidTicketAccess() {
        ArcaCache cache = new InMemoryArcaCache();
        WsaaClient client = new WsaaClient(propsWithStub(), NOOP_SIGNER, cache);

        TicketAccess ta = client.obtener("wsfe");

        assertNotNull(ta);
        assertEquals("TOKEN_TEST", ta.token());
        assertEquals("SIGN_TEST", ta.sign());
        assertTrue(ta.expiresAt().isAfter(Instant.now()));
    }

    @Test
    void obtener_reusesCachedTicket_noSecondHttpCall() {
        ArcaCache cache = new InMemoryArcaCache();
        WsaaClient client = new WsaaClient(propsWithStub(), NOOP_SIGNER, cache);

        TicketAccess first = client.obtener("wsfe");
        TicketAccess second = client.obtener("wsfe");

        assertEquals(first.token(),  second.token(),
                "Token must match — served from cache");
        assertEquals(1, hitCount.get(), "WSAA must be called exactly once");
    }

    @Test
    void obtener_differentServices_hitWsaaTwice() {
        ArcaCache cache = new InMemoryArcaCache();
        WsaaClient client = new WsaaClient(propsWithStub(), NOOP_SIGNER, cache);

        client.obtener("wsfe");
        client.obtener("ws_sr_constancia_inscripcion");

        assertEquals(2, hitCount.get(), "Each service needs its own TA");
    }

    @Test
    void obtener_afterCacheEviction_fetchesNewTicket() {
        ArcaCache cache = new InMemoryArcaCache();
        WsaaClient client = new WsaaClient(propsWithStub(), NOOP_SIGNER, cache);

        client.obtener("wsfe");
        cache.evict("ta:20123456789:wsfe");
        client.obtener("wsfe");

        assertEquals(2, hitCount.get(), "Must fetch again after cache eviction");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String buildFakeTaResponse() {
        String expiry = OffsetDateTime.now(ZoneOffset.ofHours(-3))
                .plusHours(12)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));

        String taXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<loginTicketResponse version=\"1.0\">" +
                "<header>" +
                "<source>WSAA</source>" +
                "<destination>SERIALNUMBER=CUIT 20123456789, CN=test</destination>" +
                "<uniqueId>12345</uniqueId>" +
                "<generationTime>2026-01-01T00:00:00-03:00</generationTime>" +
                "<expirationTime>" + expiry + "</expirationTime>" +
                "</header>" +
                "<credentials>" +
                "<token>TOKEN_TEST</token>" +
                "<sign>SIGN_TEST</sign>" +
                "</credentials>" +
                "</loginTicketResponse>";

        // Wrap in SOAP envelope as WSAA does
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
               "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
               "<soapenv:Body>" +
               "<loginCmsResponse xmlns=\"http://wsaa.view.sua.dvadac.desein.afip.gov.ar\">" +
               "<loginCmsReturn>" + escapeXml(taXml) + "</loginCmsReturn>" +
               "</loginCmsResponse>" +
               "</soapenv:Body>" +
               "</soapenv:Envelope>";
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
