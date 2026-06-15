package com.github.santiescobares.jarca.spring;

import com.github.santiescobares.jarca.config.ArcaProperties;
import com.github.santiescobares.jarca.config.Environment;
import com.github.santiescobares.jarca.config.ServiceUrls;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Locale;

/**
 * Spring Boot {@link ConfigurationProperties} binding for j-arca.
 *
 * <p>Example {@code application.properties}:
 * <pre>
 * arca.environment=HOMOLOGACION
 * arca.cuit=20123456789
 * arca.certificate-path=/etc/arca/cert.crt
 * arca.private-key-path=/etc/arca/key.pem
 * arca.connect-timeout=30s
 * arca.request-timeout=60s
 *
 * # Optional URL overrides (default: ARCA published endpoints)
 * arca.urls.wsaa=https://custom-wsaa.example.com/LoginCms
 * arca.urls.wsfe=https://custom-wsfe.example.com/wsfev1/service.asmx
 * </pre>
 *
 * <p>Use a PKCS12 keystore instead of separate PEM files:
 * <pre>
 * arca.keystore-path=/etc/arca/arca.p12
 * arca.keystore-password=secret
 * </pre>
 */
@ConfigurationProperties(prefix = "arca")
public class ArcaSpringProperties {

    /** ARCA environment: {@code HOMOLOGACION} or {@code PRODUCCION}. Defaults to {@code PRODUCCION}. */
    private String environment = "PRODUCCION";

    /** Tenant CUIT (digits only, no hyphens). Required. */
    private String cuit;

    /** Path to the PEM certificate (.crt). Used when {@code keystorePath} is not set. */
    private String certificatePath;

    /** Path to the PEM private key (.key or .pem). Used when {@code keystorePath} is not set. */
    private String privateKeyPath;

    /** Path to a PKCS12 keystore (.p12 / .pfx). When set, overrides PEM paths. */
    private String keystorePath;

    /** Password for the PKCS12 keystore. */
    private String keystorePassword;

    /** TCP connect timeout for all SOAP calls. Spring Duration format (e.g. {@code 30s}). */
    private Duration connectTimeout = Duration.ofSeconds(30);

    /** Request (read) timeout for all SOAP calls. Spring Duration format (e.g. {@code 60s}). */
    private Duration requestTimeout = Duration.ofSeconds(60);

    /** Optional URL overrides per service. Defaults to the official ARCA endpoints for the chosen environment. */
    private ServiceUrlProperties urls = new ServiceUrlProperties();

    // ── Getters / Setters ────────────────────────────────────────────────────

    public String getEnvironment() { return environment; }
    public void setEnvironment(String v) { this.environment = v; }

    public String getCuit() { return cuit; }
    public void setCuit(String v) { this.cuit = v; }

    public String getCertificatePath() { return certificatePath; }
    public void setCertificatePath(String v) { this.certificatePath = v; }

    public String getPrivateKeyPath() { return privateKeyPath; }
    public void setPrivateKeyPath(String v) { this.privateKeyPath = v; }

    public String getKeystorePath() { return keystorePath; }
    public void setKeystorePath(String v) { this.keystorePath = v; }

    public String getKeystorePassword() { return keystorePassword; }
    public void setKeystorePassword(String v) { this.keystorePassword = v; }

    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration v) { this.connectTimeout = v; }

    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration v) { this.requestTimeout = v; }

    public ServiceUrlProperties getUrls() { return urls; }
    public void setUrls(ServiceUrlProperties v) { this.urls = v; }

    // ── Conversion ───────────────────────────────────────────────────────────

    /**
     * Converts this Spring-bound properties object into a plain {@link ArcaProperties} instance.
     * Called by {@link ArcaAutoConfiguration} to create the j-arca-core configuration bean.
     */
    public ArcaProperties toArcaProperties() {
        Environment env = Environment.valueOf(environment.toUpperCase(Locale.ROOT));

        ServiceUrls.Builder urlsBuilder = ServiceUrls.builder(env);
        if (urls.getWsaa() != null) urlsBuilder.wsaaUrl(urls.getWsaa());
        if (urls.getWsfe() != null) urlsBuilder.wsfeUrl(urls.getWsfe());
        if (urls.getPadron() != null) urlsBuilder.padronUrl(urls.getPadron());
        if (urls.getWscdc() != null) urlsBuilder.wscdcUrl(urls.getWscdc());

        return ArcaProperties.builder()
                .environment(env)
                .cuit(cuit)
                .certificatePath(certificatePath)
                .privateKeyPath(privateKeyPath)
                .keystorePath(keystorePath)
                .keystorePassword(keystorePassword)
                .connectTimeout(connectTimeout)
                .requestTimeout(requestTimeout)
                .serviceUrls(urlsBuilder.build())
                .build();
    }

    // ── Nested properties ────────────────────────────────────────────────────

    /**
     * Optional overrides for service endpoint URLs.
     * When a field is {@code null} the default URL for the configured environment is used.
     * Useful when ARCA migrates domains (afip.gov.ar → arca.gob.ar) between library releases.
     */
    public static class ServiceUrlProperties {

        /** WSAA endpoint override. */
        private String wsaa;
        /** WSFEv1 endpoint override. */
        private String wsfe;
        /** Padrón (personaServiceA5) endpoint override. */
        private String padron;
        /** WSCDC endpoint override. */
        private String wscdc;

        public String getWsaa() { return wsaa; }
        public void setWsaa(String v) { this.wsaa = v; }

        public String getWsfe() { return wsfe; }
        public void setWsfe(String v) { this.wsfe = v; }

        public String getPadron() { return padron; }
        public void setPadron(String v) { this.padron = v; }

        public String getWscdc() { return wscdc; }
        public void setWscdc(String v) { this.wscdc = v; }
    }
}
