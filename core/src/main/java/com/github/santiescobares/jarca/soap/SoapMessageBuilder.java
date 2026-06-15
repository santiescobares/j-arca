package com.github.santiescobares.jarca.soap;

/**
 * Builds SOAP 1.1 envelopes as UTF-8 strings.
 * Each static factory method corresponds to one ARCA operation.
 */
public final class SoapMessageBuilder {

    private static final String SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String WSAA_NS = "http://wsaa.view.sua.dvadac.desein.afip.gov.ar";
    private static final String WSFE_NS = "http://ar.gov.afip.dif.FEV1/";

    private SoapMessageBuilder() {}

    /**
     * Builds the WSAA {@code loginCms} request.
     *
     * @param base64SignedTra Base64-encoded CMS-signed TRA
     */
    public static String loginCms(String base64SignedTra) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="%s"
                                  xmlns:wsaa="%s">
                    <soapenv:Header/>
                    <soapenv:Body>
                        <wsaa:loginCms>
                            <wsaa:in0>%s</wsaa:in0>
                        </wsaa:loginCms>
                    </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(SOAP_NS, WSAA_NS, base64SignedTra);
    }

    /**
     * Builds the WSFEv1 {@code FEDummy} request (no auth required — health check).
     */
    public static String feDummy() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="%s"
                                  xmlns:ar="%s">
                    <soapenv:Header/>
                    <soapenv:Body>
                        <ar:FEDummy/>
                    </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(SOAP_NS, WSFE_NS);
    }

    /**
     * Builds a generic WSFEv1 request wrapping arbitrary body XML.
     * Used internally by WsfevClient for typed operations.
     */
    public static String wsfeRequest(String bodyContent) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="%s"
                                  xmlns:ar="%s">
                    <soapenv:Header/>
                    <soapenv:Body>
                        %s
                    </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(SOAP_NS, WSFE_NS, bodyContent);
    }
}
