package com.github.santiescobares.jarca.auth;

import com.github.santiescobares.jarca.cache.ArcaCache;
import com.github.santiescobares.jarca.cache.CacheKeys;
import com.github.santiescobares.jarca.config.ArcaProperties;
import com.github.santiescobares.jarca.crypto.CmsSigner;
import com.github.santiescobares.jarca.error.ArcaTransportException;
import com.github.santiescobares.jarca.soap.SoapClient;
import com.github.santiescobares.jarca.soap.SoapMessageBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * WSAA client: obtains and caches Tickets de Acceso (TA) via the {@code loginCms} SOAP operation.
 *
 * <p>Lazy renewal: a cached TA is reused until it has fewer than 60 seconds of validity left
 * ({@link TicketAccess#isValid()}). The cache key is {@code ta:{cuit}:{servicio}}.
 *
 * <p>The serialised cache value is {@code token\nsign\nexpiresAtEpochMilli} to avoid
 * a dependency on JSON.
 */
public final class WsaaClient {

    private static final System.Logger LOG = System.getLogger(WsaaClient.class.getName());

    /** TA TTL stored in cache: 11 h — slightly under the 12 h WSAA validity to avoid near-expiry use. */
    private static final Duration CACHE_TTL = Duration.ofHours(11);

    private final ArcaProperties props;
    private final CmsSigner signer;
    private final ArcaCache cache;
    private final SoapClient soapClient;

    public WsaaClient(ArcaProperties props, CmsSigner signer, ArcaCache cache) {
        this.props = props;
        this.signer = signer;
        this.cache = cache;
        this.soapClient = new SoapClient(props);
    }

    /** Constructor for tests that inject a custom {@link SoapClient}. */
    WsaaClient(ArcaProperties props, CmsSigner signer, ArcaCache cache, SoapClient soapClient) {
        this.props = props;
        this.signer = signer;
        this.cache = cache;
        this.soapClient = soapClient;
    }

    /**
     * Returns a valid {@link TicketAccess} for {@code servicio}, retrieving from cache when possible.
     *
     * @param servicio ARCA service name (e.g. {@code "wsfe"})
     */
    public TicketAccess obtener(String servicio) {
        String cacheKey = CacheKeys.ticketAcceso(props.getCuit(), servicio);

        Optional<String> cached = cache.get(cacheKey);
        if (cached.isPresent()) {
            TicketAccess ta = deserialize(cached.get());
            if (ta.isValid()) {
                LOG.log(System.Logger.Level.DEBUG, "TA cache hit for {0}", cacheKey);
                return ta;
            }
            cache.evict(cacheKey);
        }

        LOG.log(System.Logger.Level.INFO, "Requesting new TA from WSAA for service={0} cuit={1}",
                servicio, props.getCuit());

        TicketAccess ta = loginCms(servicio);
        cache.put(cacheKey, serialize(ta), CACHE_TTL);
        return ta;
    }

    // ── private ──────────────────────────────────────────────────────────────

    private TicketAccess loginCms(String servicio) {
        String traXml = TraBuilder.build(servicio);
        byte[] traDer = traXml.getBytes(StandardCharsets.UTF_8);
        byte[] cms = signer.sign(traDer);
        String b64Cms = Base64.getEncoder().encodeToString(cms);

        String envelope = SoapMessageBuilder.loginCms(b64Cms);
        String url = props.getServiceUrls().getWsaaUrl();
        Document doc = soapClient.post(url, "", envelope);

        return parseResponse(doc);
    }

    private TicketAccess parseResponse(Document doc) {
        // The TA XML is nested inside loginCmsReturn as text content
        NodeList returnNodes = doc.getElementsByTagName("loginCmsReturn");
        if (returnNodes.getLength() == 0) {
            returnNodes = doc.getElementsByTagName("return");
        }
        if (returnNodes.getLength() == 0) {
            throw new ArcaTransportException("WSAA response missing loginCmsReturn element");
        }

        String taXml = returnNodes.item(0).getTextContent();

        // Parse the inner TA XML
        Document taDoc;
        try {
            javax.xml.parsers.DocumentBuilderFactory dbf =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            taDoc = dbf.newDocumentBuilder()
                       .parse(new java.io.ByteArrayInputStream(
                               taXml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new ArcaTransportException("Failed to parse WSAA TA XML", e);
        }

        String token = firstText(taDoc, "token");
        String sign = firstText(taDoc, "sign");
        String expirationTime = firstText(taDoc, "expirationTime");

        if (token == null || sign == null || expirationTime == null) {
            throw new ArcaTransportException(
                    "WSAA TA XML missing token, sign, or expirationTime");
        }

        Instant expiresAt = OffsetDateTime.parse(expirationTime).toInstant();
        return new TicketAccess(token, sign, expiresAt);
    }

    private static String firstText(Document doc, String tagName) {
        NodeList nl = doc.getElementsByTagName(tagName);
        return nl.getLength() > 0 ? nl.item(0).getTextContent().trim() : null;
    }

    /** Serialises a TA to a single string for cache storage (no JSON dependency). */
    private static String serialize(TicketAccess ta) {
        return ta.token() + "\n" + ta.sign() + "\n" + ta.expiresAt().toEpochMilli();
    }

    /** Deserialises a TA from the cache string produced by {@link #serialize}. */
    private static TicketAccess deserialize(String value) {
        String[] parts = value.split("\n", 3);
        if (parts.length != 3) {
            throw new ArcaTransportException("Corrupt TA in cache: unexpected format");
        }
        return new TicketAccess(
                parts[0],
                parts[1],
                Instant.ofEpochMilli(Long.parseLong(parts[2]))
        );
    }
}
