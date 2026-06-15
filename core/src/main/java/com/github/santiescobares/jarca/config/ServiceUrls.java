package com.github.santiescobares.jarca.config;

import java.util.Objects;

/**
 * Endpoint URLs for each ARCA service, per environment.
 * All URLs are kept configurable because ARCA is migrating domains from afip.gov.ar to arca.gob.ar.
 * The WSDL of each service is the authoritative source; these defaults match the current published WSDLs.
 */
public final class ServiceUrls {

    private final String wsaaUrl;
    private final String wsfeUrl;
    private final String padronUrl;
    private final String wscdcUrl;

    public ServiceUrls(String wsaaUrl, String wsfeUrl, String padronUrl, String wscdcUrl) {
        this.wsaaUrl = Objects.requireNonNull(wsaaUrl, "wsaaUrl");
        this.wsfeUrl = Objects.requireNonNull(wsfeUrl, "wsfeUrl");
        this.padronUrl = Objects.requireNonNull(padronUrl, "padronUrl");
        this.wscdcUrl = Objects.requireNonNull(wscdcUrl, "wscdcUrl");
    }

    /** Returns default URLs for the given environment. */
    public static ServiceUrls forEnvironment(Environment env) {
        return switch (env) {
            case HOMOLOGACION -> new ServiceUrls(
                "https://wsaahomo.afip.gov.ar/ws/services/LoginCms",
                "https://wswhomo.afip.gov.ar/wsfev1/service.asmx",
                "https://awshomo.arca.gob.ar/sr-padron/webservices/personaServiceA5",
                "https://wswhomo.afip.gob.ar/WSCDC/service.asmx"
            );
            case PRODUCCION -> new ServiceUrls(
                "https://wsaa.afip.gov.ar/ws/services/LoginCms",
                "https://servicios1.afip.gov.ar/wsfev1/service.asmx",
                "https://aws.arca.gob.ar/sr-padron/webservices/personaServiceA5",
                "https://servicios1.arca.gob.ar/WSCDC/service.asmx"
            );
        };
    }

    public String getWsaaUrl() { return wsaaUrl; }
    public String getWsfeUrl() { return wsfeUrl; }
    public String getPadronUrl() { return padronUrl; }
    public String getWscdcUrl() { return wscdcUrl; }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static Builder builder(Environment env) {
        return new Builder(forEnvironment(env));
    }

    public static final class Builder {
        private String wsaaUrl;
        private String wsfeUrl;
        private String padronUrl;
        private String wscdcUrl;

        private Builder(ServiceUrls src) {
            this.wsaaUrl = src.wsaaUrl;
            this.wsfeUrl = src.wsfeUrl;
            this.padronUrl = src.padronUrl;
            this.wscdcUrl = src.wscdcUrl;
        }

        public Builder wsaaUrl(String v) { this.wsaaUrl = v; return this; }
        public Builder wsfeUrl(String v) { this.wsfeUrl = v; return this; }
        public Builder padronUrl(String v) { this.padronUrl = v; return this; }
        public Builder wscdcUrl(String v) { this.wscdcUrl = v; return this; }

        public ServiceUrls build() {
            return new ServiceUrls(wsaaUrl, wsfeUrl, padronUrl, wscdcUrl);
        }
    }
}
