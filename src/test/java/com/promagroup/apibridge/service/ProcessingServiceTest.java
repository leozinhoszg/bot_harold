package com.promagroup.apibridge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.promagroup.apibridge.cache.DedupCache;
import com.promagroup.apibridge.dto.NotificationEvent;
import com.promagroup.apibridge.entity.Integration;
import com.promagroup.apibridge.repository.SeenRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integra Extractor + Detector reais a partir de um payload cru e da config-driven serializada,
 * provando o fluxo extract -> detect da engine (independente do Telegram).
 */
@ExtendWith(MockitoExtension.class)
class ProcessingServiceTest {

    @Mock
    private SeenRecordRepository seenRecordRepository;

    private final DedupCache noopCache = new DedupCache() {
        @Override
        public boolean isKnown(Long integrationId, String businessKey) {
            return false;
        }

        @Override
        public void remember(Long integrationId, String businessKey, String hash) {
        }
    };

    @Test
    void processExtractsAndDetectsNewRecords() {
        when(seenRecordRepository.findByIntegrationIdAndBusinessKey(eq(1L), anyString()))
                .thenReturn(Optional.empty());

        ProcessingService service = new ProcessingService(
                new Extractor(),
                new Detector(seenRecordRepository, noopCache),
                new IntegrationConfigParser(new ObjectMapper()));

        Integration integration = mock(Integration.class);
        when(integration.getId()).thenReturn(1L);
        when(integration.getConfigJson()).thenReturn(
                "{\"recordsPath\":\"$.data[*]\",\"businessKey\":\"$.id\","
                        + "\"template\":\"*{title}*\",\"fields\":{\"title\":\"$.name\"}}");

        String payload = "{\"data\":[{\"id\":1,\"name\":\"A\"},{\"id\":2,\"name\":\"B\"}]}";

        List<NotificationEvent> events = service.process(integration, payload);

        assertThat(events).extracting(NotificationEvent::businessKey).containsExactly("1", "2");
        assertThat(events).allSatisfy(e -> assertThat(e.hash()).isNotBlank());
    }

    @Test
    void processReturnsEmptyOnInvalidConfigJson() {
        ProcessingService service = new ProcessingService(
                new Extractor(),
                new Detector(seenRecordRepository, noopCache),
                new IntegrationConfigParser(new ObjectMapper()));

        Integration integration = mock(Integration.class);
        when(integration.getConfigJson()).thenReturn("{ not valid json");
        when(integration.getName()).thenReturn("broken");

        assertThat(service.process(integration, "{}")).isEmpty();
    }
}
