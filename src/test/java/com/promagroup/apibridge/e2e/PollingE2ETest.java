package com.promagroup.apibridge.e2e;

import com.promagroup.apibridge.dto.PollOutcome;
import com.promagroup.apibridge.entity.AuthType;
import com.promagroup.apibridge.entity.Integration;
import com.promagroup.apibridge.entity.Notification;
import com.promagroup.apibridge.entity.NotificationStatus;
import com.promagroup.apibridge.repository.IntegrationRepository;
import com.promagroup.apibridge.repository.NotificationRepository;
import com.promagroup.apibridge.repository.SeenRecordRepository;
import com.promagroup.apibridge.service.PollingService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Walking skeleton ponta a ponta dentro do contexto Spring: uma "API externa" (MockWebServer) e um
 * "Telegram" (MockWebServer) falsos, persistencia SQLite real. Prova o fluxo completo
 * API -> extracao -> deteccao -> formatacao -> envio -> persistencia -> deduplicacao.
 *
 * <p>Nao usa Testcontainers: o Redis fica indisponivel e o cache degrada para o SQLite (fonte da
 * verdade), exatamente o cenario de resiliencia esperado.
 */
@SpringBootTest
class PollingE2ETest {

    private static final MockWebServer telegram = new MockWebServer();
    private MockWebServer backend;

    @DynamicPropertySource
    static void telegramProperties(DynamicPropertyRegistry registry) throws IOException {
        telegram.start();
        registry.add("telegram.base-url", () -> telegram.url("/").toString());
        registry.add("telegram.token", () -> "e2e-token");
    }

    @AfterAll
    static void stopTelegram() throws IOException {
        telegram.shutdown();
    }

    @Autowired
    private PollingService pollingService;
    @Autowired
    private IntegrationRepository integrationRepository;
    @Autowired
    private SeenRecordRepository seenRecordRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void startBackend() throws IOException {
        backend = new MockWebServer();
        backend.start();
    }

    @AfterEach
    void cleanup() throws IOException {
        backend.shutdown();
        notificationRepository.deleteAll();
        seenRecordRepository.deleteAll();
        integrationRepository.deleteAll();
    }

    private static MockResponse json(String body) {
        return new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody(body);
    }

    private Integration newIntegration(String url) {
        Integration integration = new Integration();
        integration.setName("e2e");
        integration.setUrl(url);
        integration.setEnabled(true);
        integration.setCron("0 * * * * *");
        integration.setAuthType(AuthType.NONE);
        integration.setChatId("999");
        integration.setConfigJson("{\"recordsPath\":\"$.data[*]\",\"businessKey\":\"$.id\","
                + "\"template\":\"<b>{title}</b>\",\"fields\":{\"title\":\"$.name\"}}");
        return integration;
    }

    @Test
    void endToEndSendsThenDeduplicates() throws Exception {
        backend.enqueue(json("{\"data\":[{\"id\":\"p1\",\"name\":\"Hello\"}]}"));
        telegram.enqueue(json("{\"ok\":true,\"result\":{\"message_id\":42}}"));

        Integration integration = integrationRepository.save(newIntegration(backend.url("/api").toString()));

        // 1a execucao: detecta 1 novo, envia e persiste
        PollOutcome first = pollingService.poll(integration);

        assertThat(first.sent()).isEqualTo(1);
        assertThat(seenRecordRepository
                .existsByIntegrationIdAndBusinessKey(integration.getId(), "p1")).isTrue();

        List<Notification> notifications = notificationRepository.findByIntegrationId(integration.getId());
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notifications.get(0).getTelegramMessageId()).isEqualTo("42");

        RecordedRequest telegramRequest = telegram.takeRequest(5, TimeUnit.SECONDS);
        assertThat(telegramRequest).isNotNull();
        assertThat(telegramRequest.getPath()).isEqualTo("/bote2e-token/sendMessage");
        assertThat(telegramRequest.getBody().readUtf8()).contains("<b>Hello</b>");

        // 2a execucao: mesmo registro -> dedup, nenhum envio novo
        backend.enqueue(json("{\"data\":[{\"id\":\"p1\",\"name\":\"Hello\"}]}"));
        PollOutcome second = pollingService.poll(integration);

        assertThat(second.newEvents()).isZero();
        assertThat(second.sent()).isZero();
        assertThat(telegram.getRequestCount()).isEqualTo(1);
    }
}
