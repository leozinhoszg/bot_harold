package com.promagroup.apibridge.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Cache de dedup no Redis. Chave: {@code dedup:{integrationId}:{businessKey}} -> hash, com TTL.
 *
 * <p>Toda operacao e envolvida em try/catch: se o Redis estiver indisponivel, {@link #isKnown}
 * retorna false (forcando o fallback ao SQLite) e {@link #remember} apenas loga. O polling
 * nunca falha por causa do cache.
 */
@Component
public class RedisDedupCache implements DedupCache {

    private static final Logger log = LoggerFactory.getLogger(RedisDedupCache.class);

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisDedupCache(StringRedisTemplate redis,
                           @Value("${app.cache.dedup-ttl:24h}") Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    @Override
    public boolean isKnown(Long integrationId, String businessKey) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(key(integrationId, businessKey)));
        } catch (RuntimeException e) {
            log.debug("Redis indisponivel em isKnown; fallback para SQLite: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void remember(Long integrationId, String businessKey, String hash) {
        try {
            redis.opsForValue().set(key(integrationId, businessKey), hash, ttl);
        } catch (RuntimeException e) {
            log.debug("Redis indisponivel em remember; ignorado: {}", e.getMessage());
        }
    }

    private String key(Long integrationId, String businessKey) {
        return "dedup:" + integrationId + ":" + businessKey;
    }
}
