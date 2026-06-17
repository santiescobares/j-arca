package com.github.santiescobares.jarca.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Central configuration for the j-arca library.
 * Carries tenant credentials, environment, endpoints, and timeouts.
 * Build via {@link #builder()}.
 */
public final class ArcaProperties {

    /** Timezone used for all TRA timestamps and date arithmetic. */
    public static final ZoneId ZONE = ZoneId.of("America/Argentina/Cordoba");

    private final Environment environment;
    private final String cuit;
    private final ServiceUrls serviceUrls;

    /** Path to the PEM certificate (.crt). Ignored when {@code keystorePath} is set. */
    private final String certificatePath;
    /** Path to the PEM private key (.key). Ignored when {@code keystorePath} is set. */
    private final String privateKeyPath;
    /** Path to the PKCS12 keystore (.p12 / .pfx). When set, overrides PEM paths. */
    private final String keystorePath;
    /** Password for the PKCS12 keystore. */
    private final String keystorePassword;

    private final Duration connectTimeout;
    private final Duration requestTimeout;
    /** WSAA TRA validity window requested in each {@code loginCms}. Defaults to 12 h. */
    private final Duration traValidity;

    private ArcaProperties(Builder b) {
        this.environment = Objects.requireNonNull(b.environment, "environment");
        this.cuit = Objects.requireNonNull(b.cuit, "cuit");
        this.serviceUrls = b.serviceUrls != null ? b.serviceUrls : ServiceUrls.forEnvironment(b.environment);
        this.certificatePath = b.certificatePath;
        this.privateKeyPath = b.privateKeyPath;
        this.keystorePath = b.keystorePath;
        this.keystorePassword = b.keystorePassword;
        this.connectTimeout = b.connectTimeout != null ? b.connectTimeout : Duration.ofSeconds(30);
        this.requestTimeout = b.requestTimeout != null ? b.requestTimeout : Duration.ofSeconds(60);
        this.traValidity = b.traValidity != null ? b.traValidity : Duration.ofHours(12);
    }

    public static Builder builder() { return new Builder(); }

    public Environment getEnvironment() { return environment; }
    public String getCuit() { return cuit; }
    public ServiceUrls getServiceUrls() { return serviceUrls; }
    public String getCertificatePath() { return certificatePath; }
    public String getPrivateKeyPath() { return privateKeyPath; }
    public String getKeystorePath() { return keystorePath; }
    public String getKeystorePassword() { return keystorePassword; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public Duration getTraValidity() { return traValidity; }

    public static final class Builder {
        private Environment environment;
        private String cuit;
        private ServiceUrls serviceUrls;
        private String certificatePath;
        private String privateKeyPath;
        private String keystorePath;
        private String keystorePassword;
        private Duration connectTimeout;
        private Duration requestTimeout;
        private Duration traValidity;

        public Builder environment(Environment v) { this.environment = v; return this; }
        public Builder cuit(String v) { this.cuit = v; return this; }
        public Builder serviceUrls(ServiceUrls v) { this.serviceUrls = v; return this; }
        public Builder certificatePath(String v) { this.certificatePath = v; return this; }
        public Builder privateKeyPath(String v) { this.privateKeyPath = v; return this; }
        public Builder keystorePath(String v) { this.keystorePath = v; return this; }
        public Builder keystorePassword(String v) { this.keystorePassword = v; return this; }
        public Builder connectTimeout(Duration v) { this.connectTimeout = v; return this; }
        public Builder requestTimeout(Duration v) { this.requestTimeout = v; return this; }
        public Builder traValidity(Duration v) { this.traValidity = v; return this; }

        public ArcaProperties build() { return new ArcaProperties(this); }
    }
}
