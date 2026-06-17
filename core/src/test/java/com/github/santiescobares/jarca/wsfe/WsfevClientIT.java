package com.github.santiescobares.jarca.wsfe;

import com.github.santiescobares.jarca.auth.TicketAccess;
import com.github.santiescobares.jarca.auth.WsaaClient;
import com.github.santiescobares.jarca.config.ArcaProperties;
import com.github.santiescobares.jarca.config.Environment;
import com.github.santiescobares.jarca.crypto.BouncyCastleCmsSigner;
import com.github.santiescobares.jarca.crypto.CertificateLoader;
import com.github.santiescobares.jarca.testsupport.FilePersistentArcaCache;
import com.github.santiescobares.jarca.testsupport.ItCredentials;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for {@link WsfevClient} against the ARCA homologación environment.
 *
 * <p>Required environment variables:
 * <ul>
 *   <li>{@code ARCA_CERT_PATH} — path to the PEM certificate (.crt)</li>
 *   <li>{@code ARCA_KEY_PATH}  — path to the PEM private key</li>
 *   <li>{@code ARCA_CUIT}      — CUIT associated with the certificate</li>
 * </ul>
 *
 * <p>Optional:
 * <ul>
 *   <li>{@code ARCA_PTO_VTA}   — punto de venta for tests (defaults to 1)</li>
 * </ul>
 *
 * <p>Run with: {@code mvn verify -Phomologacion}
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WsfevClientIT {

    private static ArcaProperties props;
    private static WsfevClient wsfevClient;
    private static WsaaClient wsaaClient;
    private static int ptoVta;

    @BeforeAll
    static void setup() {
        String certPath = ItCredentials.resolve("arca.cert", "ARCA_CERT_PATH");
        String keyPath = ItCredentials.resolve("arca.key", "ARCA_KEY_PATH");
        String cuit = ItCredentials.resolve("arca.cuit", "ARCA_CUIT");

        assumeTrue(certPath != null && keyPath != null && cuit != null,
                "Skipping IT: ARCA_CERT_PATH / ARCA_KEY_PATH / ARCA_CUIT not set");

        ptoVta = Integer.parseInt(ItCredentials.resolve("arca.ptoVta", "ARCA_PTO_VTA", "1"));

        props = ArcaProperties.builder()
                .environment(Environment.HOMOLOGACION)
                .cuit(cuit)
                .certificatePath(certPath)
                .privateKeyPath(keyPath)
                .build();

        CertificateLoader.CertAndKey ck = CertificateLoader.fromProperties(props);
        BouncyCastleCmsSigner signer = new BouncyCastleCmsSigner(ck.certificate(), ck.privateKey());
        wsaaClient = new WsaaClient(props, signer, new FilePersistentArcaCache());
        wsfevClient = new WsfevClient(props);
    }

    // ── FEDummy ──────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void feDummy_allServersOk() {
        assertTrue(wsfevClient.feDummy(),
                "FEDummy must return true when all ARCA servers are OK");
    }

    // ── FEParamGetTiposCbte ──────────────────────────────────────────────────

    @Test
    @Order(2)
    void feParamGetTiposCbte_containsExpectedTypes() {
        TicketAccess ta = wsaaClient.obtener("wsfe");

        List<Integer> tipos = wsfevClient.feParamGetTiposCbte(ta);

        assertFalse(tipos.isEmpty(), "CbteTipo list must not be empty");
        assertTrue(tipos.contains(1),  "Must include CbteTipo 1 (Factura A)");
        assertTrue(tipos.contains(6),  "Must include CbteTipo 6 (Factura B)");
        assertTrue(tipos.contains(11), "Must include CbteTipo 11 (Factura C)");
        assertTrue(tipos.contains(13), "Must include CbteTipo 13 (NC C)");
    }

    // ── FECompUltimoAutorizado ───────────────────────────────────────────────

    @Test
    @Order(3)
    void feCompUltimoAutorizado_facturaC_returnsNonNegative() {
        TicketAccess ta = wsaaClient.obtener("wsfe");

        long ultimo = wsfevClient.feCompUltimoAutorizado(ta, ptoVta, 11);

        assertTrue(ultimo >= 0,
                "Last authorised Factura C number must be >= 0, got: " + ultimo);
    }

    @Test
    @Order(4)
    void feCompUltimoAutorizado_facturaA_returnsNonNegative() {
        TicketAccess ta = wsaaClient.obtener("wsfe");

        long ultimo = wsfevClient.feCompUltimoAutorizado(ta, ptoVta, 1);

        assertTrue(ultimo >= 0,
                "Last authorised Factura A number must be >= 0, got: " + ultimo);
    }

    // ── FECompConsultar ──────────────────────────────────────────────────────

    @Test
    @Order(5)
    void feCompConsultar_absentComprobante_returnsEmpty() {
        TicketAccess ta = wsaaClient.obtener("wsfe");

        // Number 0 does not exist in ARCA; should return empty (not throw)
        var result = wsfevClient.feCompConsultar(ta, ptoVta, 11, 0L);

        assertTrue(result.isEmpty(),
                "FECompConsultar for non-existent comprobante must return empty");
    }

    @Test
    @Order(6)
    void feCompConsultar_existingComprobante_returnsResult() {
        TicketAccess ta = wsaaClient.obtener("wsfe");
        long ultimo = wsfevClient.feCompUltimoAutorizado(ta, ptoVta, 11);

        assumeTrue(ultimo > 0, "No Factura C emitted yet for ptoVta=" + ptoVta + "; skipping lookup test");

        var result = wsfevClient.feCompConsultar(ta, ptoVta, 11, ultimo);

        assertTrue(result.isPresent(),
                "FECompConsultar for the last authorised comprobante must return a result");
        assertNotNull(result.get().cae(), "CAE must be present for an authorised comprobante");
    }
}
