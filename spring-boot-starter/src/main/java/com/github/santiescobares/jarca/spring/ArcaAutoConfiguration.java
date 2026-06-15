package com.github.santiescobares.jarca.spring;

import com.github.santiescobares.jarca.auth.WsaaClient;
import com.github.santiescobares.jarca.cache.ArcaCache;
import com.github.santiescobares.jarca.cache.InMemoryArcaCache;
import com.github.santiescobares.jarca.cdc.WscdcClient;
import com.github.santiescobares.jarca.config.ArcaProperties;
import com.github.santiescobares.jarca.crypto.BouncyCastleCmsSigner;
import com.github.santiescobares.jarca.crypto.CertificateLoader;
import com.github.santiescobares.jarca.crypto.CmsSigner;
import com.github.santiescobares.jarca.padron.PadronClient;
import com.github.santiescobares.jarca.wsfe.ComprobanteService;
import com.github.santiescobares.jarca.wsfe.ComprobanteServiceImpl;
import com.github.santiescobares.jarca.wsfe.WsfevClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot auto-configuration for j-arca.
 *
 * <p>Creates the following beans (each guarded by {@code @ConditionalOnMissingBean} so
 * applications can override any of them):
 * <ul>
 *   <li>{@link ArcaProperties} — bound from {@code arca.*} in {@code application.properties}</li>
 *   <li>{@link CmsSigner} — BouncyCastle-based SHA256withRSA signer using the configured certificate</li>
 *   <li>{@link ArcaCache} — {@link SpringRedisArcaCache} when Spring Data Redis is available,
 *       otherwise {@link InMemoryArcaCache}</li>
 *   <li>{@link WsaaClient}, {@link WsfevClient}, {@link PadronClient}, {@link WscdcClient}</li>
 *   <li>{@link ComprobanteService} — orchestrates TA acquisition, numbering, and CAE request</li>
 * </ul>
 *
 * <p>Minimal {@code application.properties}:
 * <pre>
 * arca.environment=HOMOLOGACION
 * arca.cuit=20123456789
 * arca.certificate-path=/etc/arca/cert.crt
 * arca.private-key-path=/etc/arca/key.pem
 * </pre>
 *
 * <p>Auto-configured via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ArcaSpringProperties.class)
public class ArcaAutoConfiguration {

    // ── Core configuration ───────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    public ArcaProperties arcaProperties(ArcaSpringProperties springProps) {
        return springProps.toArcaProperties();
    }

    @Bean
    @ConditionalOnMissingBean
    public CmsSigner cmsSigner(ArcaProperties props) {
        CertificateLoader.CertAndKey ck = CertificateLoader.fromProperties(props);
        return new BouncyCastleCmsSigner(ck.certificate(), ck.privateKey());
    }

    // ── Service clients ──────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    public WsaaClient wsaaClient(ArcaProperties props, CmsSigner signer, ArcaCache cache) {
        return new WsaaClient(props, signer, cache);
    }

    @Bean
    @ConditionalOnMissingBean
    public WsfevClient wsfevClient(ArcaProperties props) {
        return new WsfevClient(props);
    }

    @Bean
    @ConditionalOnMissingBean
    public PadronClient padronClient(ArcaProperties props) {
        return new PadronClient(props);
    }

    @Bean
    @ConditionalOnMissingBean
    public WscdcClient wscdcClient(ArcaProperties props) {
        return new WscdcClient(props);
    }

    @Bean
    @ConditionalOnMissingBean
    public ComprobanteService comprobanteService(ArcaProperties props,
                                                  WsaaClient wsaaClient,
                                                  WsfevClient wsfevClient) {
        return new ComprobanteServiceImpl(props, wsaaClient, wsfevClient);
    }

    // ── Cache: Redis (preferred) or in-memory (fallback) ────────────────────

    /**
     * Creates a {@link SpringRedisArcaCache} when Spring Data Redis is on the classpath
     * and a {@code StringRedisTemplate} bean is present.
     *
     * <p>This inner {@code @Configuration} is evaluated BEFORE the outer class's
     * {@link #inMemoryArcaCache()} bean method, so the in-memory fallback correctly
     * defers to this cache when Redis is available.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
    static class RedisArcaCacheConfiguration {

        @Bean
        @ConditionalOnBean(type = "org.springframework.data.redis.core.StringRedisTemplate")
        @ConditionalOnMissingBean(ArcaCache.class)
        public ArcaCache arcaCache(
                org.springframework.data.redis.core.StringRedisTemplate redisTemplate) {
            return new SpringRedisArcaCache(redisTemplate);
        }
    }

    /**
     * Fallback: {@link InMemoryArcaCache} when no other {@link ArcaCache} bean has been registered.
     * Suitable for single-instance deployments or local development without Redis.
     *
     * <p>Note: in-memory cache does not survive application restarts, so every restart triggers
     * a new WSAA token request. For production, configure Redis via {@code spring.data.redis.*}.
     */
    @Bean
    @ConditionalOnMissingBean(ArcaCache.class)
    public ArcaCache inMemoryArcaCache() {
        return new InMemoryArcaCache();
    }
}
