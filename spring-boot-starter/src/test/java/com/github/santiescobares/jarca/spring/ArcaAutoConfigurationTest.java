package com.github.santiescobares.jarca.spring;

import com.github.santiescobares.jarca.cache.ArcaCache;
import com.github.santiescobares.jarca.cache.InMemoryArcaCache;
import com.github.santiescobares.jarca.config.ArcaProperties;
import com.github.santiescobares.jarca.config.Environment;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ArcaAutoConfiguration} and {@link ArcaSpringProperties}.
 * No Spring context needed — verifies bean creation logic directly.
 */
class ArcaAutoConfigurationTest {

    private final ArcaAutoConfiguration autoConfig = new ArcaAutoConfiguration();

    // ── ArcaSpringProperties.toArcaProperties ────────────────────────────────

    @Test
    void springProperties_mapsEnvironmentAndCuit() {
        ArcaSpringProperties p = new ArcaSpringProperties();
        p.setEnvironment("HOMOLOGACION");
        p.setCuit("20123456789");

        ArcaProperties props = p.toArcaProperties();

        assertEquals(Environment.HOMOLOGACION, props.getEnvironment());
        assertEquals("20123456789", props.getCuit());
    }

    @Test
    void springProperties_defaultEnvironment_isProduccion() {
        ArcaSpringProperties p = new ArcaSpringProperties();
        p.setCuit("20000000000");

        ArcaProperties props = p.toArcaProperties();

        assertEquals(Environment.PRODUCCION, props.getEnvironment());
    }

    @Test
    void springProperties_pemPaths_propagated() {
        ArcaSpringProperties p = new ArcaSpringProperties();
        p.setEnvironment("PRODUCCION");
        p.setCuit("20000000000");
        p.setCertificatePath("/etc/arca/cert.crt");
        p.setPrivateKeyPath("/etc/arca/key.pem");

        ArcaProperties props = p.toArcaProperties();

        assertEquals("/etc/arca/cert.crt", props.getCertificatePath());
        assertEquals("/etc/arca/key.pem",  props.getPrivateKeyPath());
    }

    @Test
    void springProperties_keystorePaths_propagated() {
        ArcaSpringProperties p = new ArcaSpringProperties();
        p.setEnvironment("PRODUCCION");
        p.setCuit("20000000000");
        p.setKeystorePath("/etc/arca/arca.p12");
        p.setKeystorePassword("s3cr3t");

        ArcaProperties props = p.toArcaProperties();

        assertEquals("/etc/arca/arca.p12", props.getKeystorePath());
        assertEquals("s3cr3t",              props.getKeystorePassword());
    }

    @Test
    void springProperties_timeouts_propagated() {
        ArcaSpringProperties p = new ArcaSpringProperties();
        p.setEnvironment("PRODUCCION");
        p.setCuit("20000000000");
        p.setConnectTimeout(Duration.ofSeconds(15));
        p.setRequestTimeout(Duration.ofSeconds(90));

        ArcaProperties props = p.toArcaProperties();

        assertEquals(Duration.ofSeconds(15), props.getConnectTimeout());
        assertEquals(Duration.ofSeconds(90), props.getRequestTimeout());
    }

    @Test
    void springProperties_urlOverrides_appliedToServiceUrls() {
        ArcaSpringProperties p = new ArcaSpringProperties();
        p.setEnvironment("PRODUCCION");
        p.setCuit("20000000000");

        ArcaSpringProperties.ServiceUrlProperties urls = new ArcaSpringProperties.ServiceUrlProperties();
        urls.setWsfe("https://custom.endpoint/wsfev1/service.asmx");
        urls.setWsaa("https://custom.endpoint/wsaa/LoginCms");
        p.setUrls(urls);

        ArcaProperties props = p.toArcaProperties();

        assertEquals("https://custom.endpoint/wsfev1/service.asmx", props.getServiceUrls().getWsfeUrl());
        assertEquals("https://custom.endpoint/wsaa/LoginCms",        props.getServiceUrls().getWsaaUrl());
        // Non-overridden URLs keep the production defaults
        assertNotNull(props.getServiceUrls().getPadronUrl());
        assertNotNull(props.getServiceUrls().getWscdcUrl());
    }

    @Test
    void springProperties_defaultUrls_matchEnvironment() {
        ArcaSpringProperties homo = new ArcaSpringProperties();
        homo.setEnvironment("HOMOLOGACION");
        homo.setCuit("20000000000");

        ArcaSpringProperties prod = new ArcaSpringProperties();
        prod.setEnvironment("PRODUCCION");
        prod.setCuit("20000000000");

        ArcaProperties homoProps = homo.toArcaProperties();
        ArcaProperties prodProps = prod.toArcaProperties();

        // Homologación uses wsaahomo / wswhomo endpoints
        assertTrue(homoProps.getServiceUrls().getWsaaUrl().contains("homo"),
                "HOMOLOGACION WSAA URL should contain 'homo'");
        assertFalse(prodProps.getServiceUrls().getWsaaUrl().contains("homo"),
                "PRODUCCION WSAA URL should not contain 'homo'");
    }

    // ── ArcaAutoConfiguration.inMemoryArcaCache ──────────────────────────────

    @Test
    void inMemoryArcaCache_defaultBean_isInMemoryArcaCache() {
        ArcaCache cache = autoConfig.inMemoryArcaCache();

        assertNotNull(cache);
        assertInstanceOf(InMemoryArcaCache.class, cache);
    }

    @Test
    void inMemoryArcaCache_putAndGet_work() {
        ArcaCache cache = autoConfig.inMemoryArcaCache();

        cache.put("ta:20123456789:wsfe", "token\nsign\n9999999999999", Duration.ofHours(11));
        assertEquals(Optional.of("token\nsign\n9999999999999"),
                cache.get("ta:20123456789:wsfe"));
    }

    @Test
    void inMemoryArcaCache_evict_removesEntry() {
        ArcaCache cache = autoConfig.inMemoryArcaCache();
        cache.put("param:wsfe:TiposCbte", "[1,6,11]", Duration.ofHours(24));

        cache.evict("param:wsfe:TiposCbte");

        assertTrue(cache.get("param:wsfe:TiposCbte").isEmpty());
    }

    // ── ArcaAutoConfiguration.arcaProperties ─────────────────────────────────

    @Test
    void arcaProperties_delegatesToSpringProperties() {
        ArcaSpringProperties p = new ArcaSpringProperties();
        p.setEnvironment("HOMOLOGACION");
        p.setCuit("20999999999");

        ArcaProperties props = autoConfig.arcaProperties(p);

        assertEquals(Environment.HOMOLOGACION, props.getEnvironment());
        assertEquals("20999999999", props.getCuit());
    }
}
