package com.promagroup.apibridge.service;

import com.promagroup.apibridge.client.ApiClient;
import com.promagroup.apibridge.client.ApiClientException;
import com.promagroup.apibridge.dto.ApiResponse;
import com.promagroup.apibridge.dto.IntegrationConfig;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orquestra o fluxo completo de uma integracao: adquire o lock, chama a API (com paginacao),
 * processa (extrai + detecta novos), formata e envia ao Telegram, e so entao marca como
 * notificado (persistindo em seen_records). Uma falha de envio deixa o registro para o proximo
 * poll (nao chama markNotified). Tolerante a falhas: excecoes sao logadas e nao propagadas.
 */
@Service
public class PollingService {

    private static final Logger log = LoggerFactory.getLogger(PollingService.class);

    private final ApiClient apiClient;
    private final ProcessingService processingService;
    private final IntegrationConfigParser configParser;
    private final Formatter formatter;
    private final TelegramPublisher telegramPublisher;
    private final Detector detector;
    private final NotificationRepository notificationRepository;
    private final TelegramProperties telegramProperties;
    private final PollingLock pollingLock;
    private final MeterRegistry meterRegistry;

    public PollingService(ApiClient apiClient,
                          ProcessingService processingService,
                          IntegrationConfigParser configParser,
                          Formatter formatter,
                          TelegramPublisher telegramPublisher,
                          Detector detector,
                          NotificationRepository notificationRepository,
                          TelegramProperties telegramProperties,
                          PollingLock pollingLock,
                          MeterRegistry meterRegistry) {
        this.apiClient = apiClient;
        this.processingService = processingService;
        this.configParser = configParser;
        this.formatter = formatter;
        this.telegramPublisher = telegramPublisher;
        this.detector = detector;
        this.notificationRepository = notificationRepository;
        this.telegramProperties = telegramProperties;
        this.pollingLock = pollingLock;
        this.meterRegistry = meterRegistry;
    }

    public PollOutcome poll(Integration integration) {
        Long integrationId = integration.getId();
        String name = integration.getName();

        if (!pollingLock.tryAcquire(integrationId)) {
            log.info("Polling '{}' pulado: ja em andamento", name);
            meterRegistry.counter("apibridge.polls.skipped", "integration", name).increment();
            return PollOutcome.skipped(name);
        }

        long startNanos = System.nanoTime();
        try {
            IntegrationConfig config = configParser.parse(integration);
            if (config == null) {
                log.warn("Polling '{}' abortado: config invalida", name);
                return PollOutcome.empty(name);
            }

            log.info("Polling '{}' iniciado", name);
            List<ApiResponse<String>> pages = apiClient.fetchAllPages(integration);
            List<NotificationEvent> events = collectNewEvents(integration, pages);

            int sent = 0;
            int failed = 0;
            for (NotificationEvent event : events) {
                if (publish(integration, config, event)) {
                    sent++;
                } else {
                    failed++;
                }
            }

            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            recordMetrics(name, sent, failed);
            log.info("Polling '{}' concluido: {} novos, {} enviados, {} falhas em {}ms",
                    name, events.size(), sent, failed, elapsedMs);
            return new PollOutcome(name, events.size(), sent, failed, false);
        } catch (ApiClientException e) {
            log.warn("Polling '{}' falhou na chamada da API: {}", name, e.getMessage());
            return PollOutcome.empty(name);
        } catch (RuntimeException e) {
            log.error("Polling '{}' erro inesperado", name, e);
            return PollOutcome.empty(name);
        } finally {
            pollingLock.release(integrationId);
        }
    }

    /** Processa todas as paginas e deduplica por businessKey (o mesmo item pode aparecer em 2 paginas). */
    private List<NotificationEvent> collectNewEvents(Integration integration, List<ApiResponse<String>> pages) {
        Map<String, NotificationEvent> byKey = new LinkedHashMap<>();
        for (ApiResponse<String> page : pages) {
            for (NotificationEvent event : processingService.process(integration, page.payload())) {
                byKey.putIfAbsent(event.businessKey(), event);
            }
        }
        return new ArrayList<>(byKey.values());
    }

    private boolean publish(Integration integration, IntegrationConfig config, NotificationEvent event) {
        String chatId = resolveChatId(integration);
        if (chatId == null || chatId.isBlank()) {
            log.error("Sem chatId para '{}' (nem default); evento {} descartado",
                    integration.getName(), event.businessKey());
            return false;
        }

        String message = formatter.render(config.template(), event.fields(), config.effectiveParseMode());
        Notification notification = notificationRepository.save(
                new Notification(integration.getId(), event.businessKey(), message));

        try {
            String messageId = telegramPublisher.sendMessage(chatId, message, config.effectiveParseMode());
            notification.setTelegramMessageId(messageId);
            notification.setStatus(NotificationStatus.SENT);
            notificationRepository.save(notification);
            // So marca como visto APOS o envio confirmado -> falha e re-tentada no proximo poll
            detector.markNotified(event);
            return true;
        } catch (TelegramException e) {
            notification.setStatus(NotificationStatus.FAILED);
            notificationRepository.save(notification);
            log.warn("Falha ao enviar evento {} de '{}': {}",
                    event.businessKey(), integration.getName(), e.getMessage());
            return false;
        }
    }

    private void recordMetrics(String name, int sent, int failed) {
        meterRegistry.counter("apibridge.polls", "integration", name).increment();
        meterRegistry.counter("apibridge.notifications.sent", "integration", name).increment(sent);
        meterRegistry.counter("apibridge.notifications.failed", "integration", name).increment(failed);
    }

    private String resolveChatId(Integration integration) {
        if (integration.getChatId() != null && !integration.getChatId().isBlank()) {
            return integration.getChatId();
        }
        return telegramProperties.defaultChat();
    }
}
