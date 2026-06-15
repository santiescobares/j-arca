package com.github.santiescobares.jarca.auth;

import com.github.santiescobares.jarca.cache.InMemoryArcaCache;
import com.github.santiescobares.jarca.config.ArcaProperties;
import com.github.santiescobares.jarca.config.Environment;
import com.github.santiescobares.jarca.crypto.BouncyCastleCmsSigner;
import com.github.santiescobares.jarca.crypto.CertificateLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: hits ARCA homologación — requires real credentials via env vars.
 * Run with: mvn verify -Phomologacion
 * Required env vars: ARCA_CERT_PATH, ARCA_KEY_PATH, ARCA_CUIT
 */
class WsaaClientIT {

    @Test
    void obtener_validTicketFromHomologacion() {
        String certPath = System.getenv("ARCA_CERT_PATH");
        String keyPath  = System.getenv("ARCA_KEY_PATH");
        String cuit     = System.getenv("ARCA_CUIT");

        Assumptions.assumeTrue(certPath != null && keyPath != null && cuit != null,
                "Skipping IT: ARCA_CERT_PATH / ARCA_KEY_PATH / ARCA_CUIT not set");

        ArcaProperties props = ArcaProperties.builder()
                .environment(Environment.HOMOLOGACION)
                .cuit(cuit)
                .certificatePath(certPath)
                .privateKeyPath(keyPath)
                .build();

        CertificateLoader.CertAndKey ck = CertificateLoader.fromProperties(props);
        BouncyCastleCmsSigner signer    = new BouncyCastleCmsSigner(ck.certificate(), ck.privateKey());
        InMemoryArcaCache cache         = new InMemoryArcaCache();
        WsaaClient client               = new WsaaClient(props, signer, cache);

        // First call: must contact WSAA
        TicketAccess ta1 = client.obtener("wsfe");
        assertNotNull(ta1.token(),    "Token must not be null");
        assertNotNull(ta1.sign(),     "Sign must not be null");
        assertTrue(ta1.expiresAt().isAfter(Instant.now()), "TA must not already be expired");
        assertTrue(ta1.isValid(), "TA must be valid with 60s margin");

        // Second call: must use cache — no new HTTP call
        TicketAccess ta2 = client.obtener("wsfe");
        assertEquals(ta1.token(), ta2.token(), "Cached token must match");
        assertEquals(ta1.sign(),  ta2.sign(),  "Cached sign must match");
    }
}
