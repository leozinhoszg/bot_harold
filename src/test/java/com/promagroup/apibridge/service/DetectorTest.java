package com.promagroup.apibridge.service;

import com.promagroup.apibridge.cache.DedupCache;
import com.promagroup.apibridge.dto.ExtractedRecord;
import com.promagroup.apibridge.dto.NotificationEvent;
import com.promagroup.apibridge.entity.Integration;
import com.promagroup.apibridge.entity.SeenRecord;
import com.promagroup.apibridge.repository.SeenRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DetectorTest {

    @Mock
    private SeenRecordRepository seenRecordRepository;

    /** Cache em memoria para observar aquecimento sem depender de Redis. */
    private static final class FakeCache implements DedupCache {
        final Map<String, String> store = new HashMap<>();

        @Override
        public boolean isKnown(Long integrationId, String businessKey) {
            return store.containsKey(integrationId + ":" + businessKey);
        }

        @Override
        public void remember(Long integrationId, String businessKey, String hash) {
            store.put(integrationId + ":" + businessKey, hash);
        }
    }

    private final FakeCache cache = new FakeCache();

    private Detector detector() {
        return new Detector(seenRecordRepository, cache);
    }

    private Integration integrationWithId(long id) {
        Integration integration = mock(Integration.class);
        when(integration.getId()).thenReturn(id);
        return integration;
    }

    private static ExtractedRecord record(String key) {
        return new ExtractedRecord(key, Map.of("title", "v-" + key));
    }

    @Test
    void newRecordProducesEvent() {
        when(seenRecordRepository.findByIntegrationIdAndBusinessKey(1L, "k1")).thenReturn(Optional.empty());

        List<NotificationEvent> events = detector().detectNew(integrationWithId(1L), List.of(record("k1")));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).businessKey()).isEqualTo("k1");
        assertThat(events.get(0).hash()).isNotBlank();
    }

    @Test
    void recordKnownInCacheIsSkippedWithoutHittingDb() {
        cache.remember(1L, "k1", "h");

        List<NotificationEvent> events = detector().detectNew(integrationWithId(1L), List.of(record("k1")));

        assertThat(events).isEmpty();
        verify(seenRecordRepository, never()).findByIntegrationIdAndBusinessKey(any(), any());
    }

    @Test
    void recordKnownInDbIsSkippedAndWarmsCache() {
        when(seenRecordRepository.findByIntegrationIdAndBusinessKey(1L, "k1"))
                .thenReturn(Optional.of(new SeenRecord(1L, "k1", "stored-hash")));

        List<NotificationEvent> events = detector().detectNew(integrationWithId(1L), List.of(record("k1")));

        assertThat(events).isEmpty();
        assertThat(cache.isKnown(1L, "k1")).isTrue();
    }

    @Test
    void duplicateBusinessKeyInBatchYieldsSingleEvent() {
        when(seenRecordRepository.findByIntegrationIdAndBusinessKey(1L, "k1")).thenReturn(Optional.empty());

        List<NotificationEvent> events =
                detector().detectNew(integrationWithId(1L), List.of(record("k1"), record("k1")));

        assertThat(events).hasSize(1);
    }

    @Test
    void markNotifiedPersistsAndCaches() {
        when(seenRecordRepository.existsByIntegrationIdAndBusinessKey(1L, "k1")).thenReturn(false);
        NotificationEvent event = new NotificationEvent(1L, "k1", "h1", Map.of("title", "X"));

        detector().markNotified(event);

        ArgumentCaptor<SeenRecord> captor = ArgumentCaptor.forClass(SeenRecord.class);
        verify(seenRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getBusinessKey()).isEqualTo("k1");
        assertThat(captor.getValue().getHash()).isEqualTo("h1");
        assertThat(cache.isKnown(1L, "k1")).isTrue();
    }

    @Test
    void markNotifiedIsIdempotentWhenAlreadyPersisted() {
        when(seenRecordRepository.existsByIntegrationIdAndBusinessKey(1L, "k1")).thenReturn(true);

        detector().markNotified(new NotificationEvent(1L, "k1", "h1", Map.of()));

        verify(seenRecordRepository, never()).save(any());
        assertThat(cache.isKnown(1L, "k1")).isTrue();
    }
}
