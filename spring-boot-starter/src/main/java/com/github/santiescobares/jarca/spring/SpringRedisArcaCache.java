package com.github.santiescobares.jarca.spring;

import com.github.santiescobares.jarca.cache.ArcaCache;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link ArcaCache} adapter backed by Spring Data Redis ({@link StringRedisTemplate}).
 *
 * <p>Used by {@link ArcaAutoConfiguration} when {@code spring-data-redis} is on the classpath
 * and a {@code StringRedisTemplate} bean is present. All operations delegate to
 * {@code StringRedisTemplate.opsForValue()}, which leverages Spring Boot's auto-configured
 * Lettuce connection pool.
 *
 * <p>For environments without Spring, use {@code RedisArcaCache} from the
 * {@code j-arca-cache-redis} module instead.
 */
public class SpringRedisArcaCache implements ArcaCache {

    private final StringRedisTemplate redis;

    public SpringRedisArcaCache(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis, "redis");
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(redis.opsForValue().get(key));
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        redis.opsForValue().set(key, value, ttl);
    }

    @Override
    public void evict(String key) {
        redis.delete(key);
    }
}
