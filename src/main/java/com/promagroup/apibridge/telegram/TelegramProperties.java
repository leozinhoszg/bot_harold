package com.promagroup.apibridge.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuracao do Telegram ({@code telegram}). O token vem de variavel de ambiente
 * (nunca commitado). {@code baseUrl} e sobrescrito nos testes para apontar ao MockWebServer.
 *
 * @param token            token do bot (BotFather), via env {@code TELEGRAM_BOT_TOKEN}
 * @param defaultChat      chat destino padrao, quando a integracao nao define {@code chatId}
 * @param baseUrl          base da Bot API (default {@code https://api.telegram.org})
 * @param timeout          timeout por tentativa (meta de entrega < 3s)
 * @param retryMaxAttempts retries alem da tentativa inicial (5xx/429/timeout)
 * @param retryBackoff     backoff inicial exponencial entre retries
 */
@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(
        String token,
        String defaultChat,
        String baseUrl,
        Duration timeout,
        int retryMaxAttempts,
        Duration retryBackoff
) {

    public TelegramProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.telegram.org";
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(3);
        }
        if (retryBackoff == null) {
            retryBackoff = Duration.ofMillis(300);
        }
        if (retryMaxAttempts < 0) {
            retryMaxAttempts = 0;
        }
    }
}
