package com.promagroup.apibridge.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Parametros de rede/resiliencia do {@link ApiClient} ({@code app.client}).
 *
 * @param connectTimeout   timeout de conexao
 * @param responseTimeout  timeout de resposta por tentativa
 * @param retryMaxAttempts numero de retries (alem da tentativa inicial) para erros transitorios
 * @param retryBackoff     backoff inicial (exponencial) entre retries
 * @param maxInMemoryBytes limite de buffer para payloads grandes
 */
@ConfigurationProperties(prefix = "app.client")
public record ApiClientProperties(
        Duration connectTimeout,
        Duration responseTimeout,
        int retryMaxAttempts,
        Duration retryBackoff,
        int maxInMemoryBytes
) {

    public ApiClientProperties {
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(2);
        }
        if (responseTimeout == null) {
            responseTimeout = Duration.ofSeconds(2);
        }
        if (retryBackoff == null) {
            retryBackoff = Duration.ofMillis(200);
        }
        if (retryMaxAttempts < 0) {
            retryMaxAttempts = 0;
        }
        if (maxInMemoryBytes <= 0) {
            maxInMemoryBytes = 10 * 1024 * 1024;
        }
    }
}
