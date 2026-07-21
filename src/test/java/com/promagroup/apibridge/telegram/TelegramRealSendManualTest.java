package com.promagroup.apibridge.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.promagroup.apibridge.dto.ParseMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Envio REAL ao Telegram — desabilitado por padrao. Roda somente quando as variaveis de ambiente
 * {@code TELEGRAM_BOT_TOKEN} (do BotFather) e {@code TELEGRAM_TEST_CHAT} estao definidas.
 *
 * <p>Como rodar (PowerShell):
 * <pre>
 *   $env:TELEGRAM_BOT_TOKEN = "123456:ABC..."
 *   $env:TELEGRAM_TEST_CHAT = "-1001234567890"
 *   .\mvnw.cmd test -Dtest=TelegramRealSendManualTest
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "TELEGRAM_BOT_TOKEN", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TELEGRAM_TEST_CHAT", matches = ".+")
class TelegramRealSendManualTest {

    @Test
    void sendsRealMessage() {
        TelegramProperties props = new TelegramProperties(
                System.getenv("TELEGRAM_BOT_TOKEN"),
                null,
                null, // baseUrl default => https://api.telegram.org
                Duration.ofSeconds(5), 1, Duration.ofMillis(300));

        TelegramPublisher publisher = new TelegramPublisher(WebClient.builder(), props, new ObjectMapper());

        String messageId = publisher.sendMessage(
                System.getenv("TELEGRAM_TEST_CHAT"),
                "<b>API Bridge Bot</b> ✅ envio de teste (Fase 4)",
                ParseMode.HTML);

        assertThat(messageId).isNotBlank();
    }
}
