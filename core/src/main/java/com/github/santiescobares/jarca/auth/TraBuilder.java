package com.github.santiescobares.jarca.auth;

import com.github.santiescobares.jarca.config.ArcaProperties;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Builds the TRA (Ticket de Requerimiento de Acceso) XML required by WSAA.
 * The TRA is then CMS-signed and Base64-encoded before being sent to {@code loginCms}.
 *
 * <p>Format per WSAA specification:
 * <pre>{@code
 * <loginTicketRequest version="1.0">
 *   <header>
 *     <uniqueId>UNIX_EPOCH_SECONDS</uniqueId>
 *     <generationTime>ISO8601_WITH_OFFSET</generationTime>
 *     <expirationTime>ISO8601_WITH_OFFSET</expirationTime>
 *   </header>
 *   <service>wsfe</service>
 * </loginTicketRequest>
 * }</pre>
 */
public final class TraBuilder {

    /** TRA validity window. WSAA allows up to 24 h; use 12 h to match the TA lifetime. */
    private static final Duration VALIDITY = Duration.ofHours(12);

    private static final DateTimeFormatter ISO_OFFSET =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private TraBuilder() {}

    /**
     * Builds a TRA XML string for the given service name.
     *
     * @param service ARCA service name, e.g. {@code "wsfe"}, {@code "ws_sr_constancia_inscripcion"}
     * @return UTF-8 XML string ready to be signed
     */
    public static String build(String service) {
        // Subtract a small buffer to tolerate clock skew between client and WSAA servers.
        ZonedDateTime now = ZonedDateTime.now(ArcaProperties.ZONE).minusSeconds(10);
        ZonedDateTime expiry = now.plus(VALIDITY);
        // Random uniqueId within xs:unsignedInt range (max 4,294,967,295) to avoid collisions across rapid TA requests.
        long uniqueId = ThreadLocalRandom.current().nextLong(1_000_000_000L, 4_000_000_000L);

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
               "<loginTicketRequest version=\"1.0\">" +
               "<header>" +
               "<uniqueId>" + uniqueId + "</uniqueId>" +
               "<generationTime>" + now.format(ISO_OFFSET) + "</generationTime>" +
               "<expirationTime>" + expiry.format(ISO_OFFSET) + "</expirationTime>" +
               "</header>" +
               "<service>" + service + "</service>" +
               "</loginTicketRequest>";
    }
}
