package com.promagroup.apibridge.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integracao real do cache de dedup contra um Redis em container.
 *
 * <p>{@code disabledWithoutDocker = true}: a classe inteira e pulada quando nao ha Docker
 * (ex.: engine desligado nesta maquina); roda em CI/ambientes com Docker. Nomeada com sufixo
 * {@code Test} para que o Surefire a execute (e o skip ocorra) sem exigir o plugin failsafe.
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisDedupCacheContainerTest {

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private RedisDedupCache cache;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        cache = new RedisDedupCache(template, Duration.ofMinutes(5));
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    void remembersAndRecognizesKeys() {
        assertThat(cache.isKnown(1L, "k1")).isFalse();

        cache.remember(1L, "k1", "hash-1");

        assertThat(cache.isKnown(1L, "k1")).isTrue();
        assertThat(cache.isKnown(1L, "outra")).isFalse();
        assertThat(cache.isKnown(2L, "k1")).isFalse();
    }
}
