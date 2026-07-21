package com.promagroup.apibridge.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Foco: degradacao graciosa. O Redis real e exercitado nos testes de integracao (Fase 6).
 */
@ExtendWith(MockitoExtension.class)
class RedisDedupCacheTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private RedisDedupCache cache() {
        return new RedisDedupCache(redis, Duration.ofHours(24));
    }

    @Test
    void isKnownReturnsTrueWhenKeyExists() {
        when(redis.hasKey("dedup:1:k1")).thenReturn(true);
        assertThat(cache().isKnown(1L, "k1")).isTrue();
    }

    @Test
    void isKnownReturnsFalseWhenRedisThrows() {
        when(redis.hasKey(anyString())).thenThrow(new RuntimeException("connection refused"));
        assertThat(cache().isKnown(1L, "k1")).isFalse();
    }

    @Test
    void rememberSetsValueWithTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);
        cache().remember(1L, "k1", "h1");
        verify(valueOps).set(eq("dedup:1:k1"), eq("h1"), any(Duration.class));
    }

    @Test
    void rememberSwallowsRedisFailure() {
        when(redis.opsForValue()).thenThrow(new RuntimeException("down"));
        assertThatCode(() -> cache().remember(1L, "k1", "h1")).doesNotThrowAnyException();
    }
}
