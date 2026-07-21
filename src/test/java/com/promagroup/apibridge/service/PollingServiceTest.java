package com.promagroup.apibridge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.promagroup.apibridge.client.ApiClient;
import com.promagroup.apibridge.dto.ApiResponse;
import com.promagroup.apibridge.dto.NotificationEvent;
import com.promagroup.apibridge.dto.PollOutcome;
import com.promagroup.apibridge.entity.Integration;
import com.promagroup.apibridge.entity.Notification;
import com.promagroup.apibridge.entity.NotificationStatus;
import com.promagroup.apibridge.repository.NotificationRepository;
import com.promagroup.apibridge.telegram.Formatter;
import com.promagroup.apibridge.telegram.TelegramException;
import com.promagroup.apibridge.telegram.TelegramProperties;
import com.promagroup.apibridge.telegram.TelegramPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PollingServiceTest {

    private static final String CONFIG_JSON =
            "{\"recordsPath\":\"$.data[*]\",\"businessKey\":\"$.id\","
                    + "\"template\":\"<b>{title}</b>\",\"fields\":{\"title\":\"$.name\"}}";

    @Mock
    private ApiClient apiClient;
    @Mock
    private ProcessingService processingService;
    @Mock
    private TelegramPublisher telegramPublisher;
    @Mock
    private Detector detector;
    @Mock
    private NotificationRepository notificationRepository;

    private final PollingLock pollingLock = new PollingLock();
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private PollingService service(String defaultChat) {
        TelegramProperties telegramProperties = new TelegramProperties(
                "token", defaultChat, null, Duration.ofSeconds(3), 2, Duration.ofMillis(100));
        lenient().when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return new PollingService(apiClient, processingService,
                new IntegrationConfigParser(new ObjectMapper()), new Formatter(),
                telegramPublisher, detector, notificationRepository, telegramProperties, pollingLock,
                meterRegistry);
    }

    private Integration integration(String chatId) {
        Integration integration = mock(Integration.class, RETURNS_DEFAULTS);
        lenient().when(integration.getId()).thenReturn(1L);
        lenient().when(integration.getName()).thenReturn("acme");
        lenient().when(integration.getChatId()).thenReturn(chatId);
        lenient().when(integration.getConfigJson()).thenReturn(CONFIG_JSON);
        return integration;
    }

    private static ApiResponse<String> page(String payload) {
        return new ApiResponse<>(200, payload, Map.of(), Instant.now());
    }

    private static NotificationEvent event(String key, String title) {
        return new NotificationEvent(1L, key, "hash-" + key, Map.of("title", title));
    }

    @Test
    void happyPathSendsAndMarksNotified() {
        Integration integration = integration("-100999");
        when(apiClient.fetchAllPages(integration)).thenReturn(List.of(page("{}")));
        when(processingService.process(eq(integration), anyString()))
                .thenReturn(List.of(event("k1", "A"), event("k2", "B")));
        when(telegramPublisher.sendMessage(anyString(), anyString(), any())).thenReturn("msg-id");

        PollOutcome outcome = service(null).poll(integration);

        assertThat(outcome.newEvents()).isEqualTo(2);
        assertThat(outcome.sent()).isEqualTo(2);
        assertThat(outcome.failed()).isZero();
        verify(telegramPublisher, times(2)).sendMessage(eq("-100999"), anyString(), any());
        verify(detector, times(2)).markNotified(any());
        assertThat(meterRegistry.get("apibridge.notifications.sent")
                .tag("integration", "acme").counter().count()).isEqualTo(2.0);
    }

    @Test
    void escapesValuesAndSendsRenderedMessage() {
        Integration integration = integration("-100999");
        when(apiClient.fetchAllPages(integration)).thenReturn(List.of(page("{}")));
        when(processingService.process(eq(integration), anyString()))
                .thenReturn(List.of(event("k1", "A & B")));
        when(telegramPublisher.sendMessage(anyString(), anyString(), any())).thenReturn("msg-id");

        service(null).poll(integration);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(telegramPublisher).sendMessage(eq("-100999"), text.capture(), any());
        assertThat(text.getValue()).isEqualTo("<b>A &amp; B</b>");
    }

    @Test
    void telegramFailureMarksFailedAndSkipsMarkNotified() {
        Integration integration = integration("-100999");
        when(apiClient.fetchAllPages(integration)).thenReturn(List.of(page("{}")));
        when(processingService.process(eq(integration), anyString()))
                .thenReturn(List.of(event("k1", "A")));
        when(telegramPublisher.sendMessage(anyString(), anyString(), any()))
                .thenThrow(new TelegramException("boom", true));

        PollOutcome outcome = service(null).poll(integration);

        assertThat(outcome.sent()).isZero();
        assertThat(outcome.failed()).isEqualTo(1);
        verify(detector, never()).markNotified(any());
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void skippedWhenLockAlreadyHeld() {
        Integration integration = integration("-100999");
        pollingLock.tryAcquire(1L); // simula poll concorrente em andamento

        PollOutcome outcome = service(null).poll(integration);

        assertThat(outcome.skipped()).isTrue();
        verify(apiClient, never()).fetchAllPages(any());
    }

    @Test
    void deduplicatesSameKeyAcrossPages() {
        Integration integration = integration("-100999");
        when(apiClient.fetchAllPages(integration)).thenReturn(List.of(page("p1"), page("p2")));
        when(processingService.process(eq(integration), anyString()))
                .thenReturn(List.of(event("k1", "A"))); // mesma businessKey nas duas paginas
        when(telegramPublisher.sendMessage(anyString(), anyString(), any())).thenReturn("msg-id");

        PollOutcome outcome = service(null).poll(integration);

        assertThat(outcome.newEvents()).isEqualTo(1);
        verify(telegramPublisher, times(1)).sendMessage(anyString(), anyString(), any());
    }

    @Test
    void usesDefaultChatWhenIntegrationChatIsNull() {
        Integration integration = integration(null);
        when(apiClient.fetchAllPages(integration)).thenReturn(List.of(page("{}")));
        when(processingService.process(eq(integration), anyString()))
                .thenReturn(List.of(event("k1", "A")));
        when(telegramPublisher.sendMessage(anyString(), anyString(), any())).thenReturn("msg-id");

        service("default-chat").poll(integration);

        verify(telegramPublisher).sendMessage(eq("default-chat"), anyString(), any());
    }
}
