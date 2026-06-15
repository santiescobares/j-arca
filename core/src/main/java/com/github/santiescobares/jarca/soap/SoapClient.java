package com.github.santiescobares.jarca.soap;

import com.github.santiescobares.jarca.config.ArcaProperties;
import com.github.santiescobares.jarca.error.ArcaTransportException;
import org.w3c.dom.Document;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Low-level SOAP 1.1 client built on {@link java.net.http.HttpClient} (JDK 21).
 * Enforces TLS 1.2+ explicitly via {@link SSLContext} and {@link SSLParameters}.
 * Secrets (token, sign, in0 payload) are masked before logging.
 */
public final class SoapClient {

    private static final System.Logger LOG = System.getLogger(SoapClient.class.getName());

    /** Masks the CMS payload sent to WSAA (can be >2 KB). */
    private static final Pattern MASK_IN0 =
            Pattern.compile("<(wsaa:)?in0>[^<]{8}[^<]*</[^>]*in0>", Pattern.CASE_INSENSITIVE);
    private static final Pattern MASK_TOKEN =
            Pattern.compile("<token>[^<]+</token>", Pattern.CASE_INSENSITIVE);
    private static final Pattern MASK_SIGN =
            Pattern.compile("<sign>[^<]+</sign>", Pattern.CASE_INSENSITIVE);

    private final HttpClient http;
    private final Duration requestTimeout;

    public SoapClient(ArcaProperties props) {
        this.requestTimeout = props.getRequestTimeout();
        boolean insecure = Boolean.getBoolean("arca.ssl.insecure");
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(props.getConnectTimeout())
                .sslContext(insecure ? insecureSslContext() : tlsContext());
        if (!insecure) {
            builder.sslParameters(tlsParameters());
        } else {
            LOG.log(System.Logger.Level.WARNING,
                    "SSL certificate validation is DISABLED (arca.ssl.insecure=true). Do not use in production.");
        }
        this.http = builder.build();
    }

    private static SSLContext tlsContext() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, null, new SecureRandom());
            return ctx;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IllegalStateException("TLS not available", e);
        }
    }

    private static SSLParameters tlsParameters() {
        SSLParameters params = new SSLParameters();
        params.setProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
        return params;
    }

    /** Trust-all SSL context for testing only. Never use in production. */
    private static SSLContext insecureSslContext() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, null);
            return ctx;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IllegalStateException("Failed to create insecure SSL context", e);
        }
    }

    /** Package-private constructor for tests that supply a custom HttpClient. */
    SoapClient(HttpClient http, Duration requestTimeout) {
        this.http           = http;
        this.requestTimeout = requestTimeout;
    }

    /**
     * Sends a SOAP 1.1 request and returns the parsed response document.
     *
     * @param url        service endpoint URL
     * @param soapAction SOAPAction header value (use {@code ""} for WSAA)
     * @param body       full SOAP envelope XML
     * @return parsed XML document of the SOAP response
     * @throws ArcaTransportException on any network, HTTP, or XML parse error
     */
    public Document post(String url, String soapAction, String body) {
        LOG.log(System.Logger.Level.DEBUG, "SOAP → {0}\n{1}", url, maskSecrets(body));

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(requestTimeout)
                    .header("Content-Type", "text/xml; charset=utf-8")
                    .header("SOAPAction", "\"" + soapAction + "\"")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
        } catch (IllegalArgumentException e) {
            throw new ArcaTransportException("Invalid SOAP request URI: " + url, e);
        }

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.net.http.HttpTimeoutException e) {
            throw new ArcaTransportException("Timeout communicating with ARCA at " + url, e);
        } catch (Exception e) {
            throw new ArcaTransportException("Network error communicating with ARCA at " + url, e);
        }

        String responseBody = response.body();
        LOG.log(System.Logger.Level.DEBUG, "SOAP ← {0} [{1}]\n{2}",
                url, response.statusCode(), maskSecrets(responseBody));

        if (response.statusCode() != 200) {
            // ARCA returns 500 for SOAP faults; parse anyway to extract the fault
            if (responseBody.contains("<Fault") || responseBody.contains(":Fault")) {
                Document doc = parseXml(responseBody, url);
                SoapFaultParser.throwIfFault(doc);
            }
            throw new ArcaTransportException(
                    "HTTP " + response.statusCode() + " from ARCA at " + url);
        }

        Document doc = parseXml(responseBody, url);
        SoapFaultParser.throwIfFault(doc);
        return doc;
    }

    private Document parseXml(String xml, String url) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            // Disable external entity processing (XXE hardening)
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new ArcaTransportException("Failed to parse XML response from " + url, e);
        }
    }

    private String maskSecrets(String xml) {
        if (xml == null) return "";
        return MASK_SIGN.matcher(
                MASK_TOKEN.matcher(
                        MASK_IN0.matcher(xml)
                                .replaceAll(m -> "<" + (m.group(1) != null ? "wsaa:" : "") + "in0>[MASKED]</" +
                                        (m.group(1) != null ? "wsaa:" : "") + "in0>")
                ).replaceAll("<token>[MASKED]</token>")
        ).replaceAll("<sign>[MASKED]</sign>");
    }
}
