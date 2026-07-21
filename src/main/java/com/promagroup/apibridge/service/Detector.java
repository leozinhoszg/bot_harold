package com.promagroup.apibridge.service;

import com.promagroup.apibridge.cache.DedupCache;
import com.promagroup.apibridge.dto.ExtractedRecord;
import com.promagroup.apibridge.dto.NotificationEvent;
import com.promagroup.apibridge.entity.Integration;
import com.promagroup.apibridge.entity.SeenRecord;
import com.promagroup.apibridge.repository.SeenRecordRepository;
import com.promagroup.apibridge.util.Hashes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Decide quais registros extraidos sao novos (ainda nao notificados) e gera os eventos.
 *
 * <p>Deduplicacao em duas camadas: cache Redis primeiro (rapido), SQLite como fonte da verdade
 * em caso de miss. Tambem deduplica dentro do mesmo lote (mesma businessKey repetida). E
 * read-only na deteccao: a marcacao de "notificado" acontece via {@link #markNotified} apos o
 * envio bem-sucedido (Fase 5), garantindo que uma falha de envio nao suprima a proxima tentativa.
 */
@Component
public class Detector {

    private static final Logger log = LoggerFactory.getLogger(Detector.class);

    private final SeenRecordRepository seenRecordRepository;
    private final DedupCache dedupCache;

    public Detector(SeenRecordRepository seenRecordRepository, DedupCache dedupCache) {
        this.seenRecordRepository = seenRecordRepository;
        this.dedupCache = dedupCache;
    }

    public List<NotificationEvent> detectNew(Integration integration, List<ExtractedRecord> records) {
        Long integrationId = integration.getId();
        List<NotificationEvent> events = new ArrayList<>();
        Set<String> batchKeys = new HashSet<>();

        for (ExtractedRecord record : records) {
            if (!batchKeys.add(record.businessKey())) {
                continue; // duplicado dentro do mesmo lote
            }
            if (isKnown(integrationId, record.businessKey())) {
                continue;
            }
            String hash = Hashes.contentHash(record.businessKey(), record.fields());
            events.add(new NotificationEvent(integrationId, record.businessKey(), hash, record.fields()));
        }

        log.debug("Integracao {}: {} registros -> {} novos", integrationId, records.size(), events.size());
        return events;
    }

    /**
     * Marca um evento como notificado: persiste em seen_records (fonte da verdade) e aquece o cache.
     * Idempotente sob corrida: se a chave ja existe, apenas reforca o cache.
     */
    @Transactional
    public void markNotified(NotificationEvent event) {
        if (seenRecordRepository.existsByIntegrationIdAndBusinessKey(event.integrationId(), event.businessKey())) {
            dedupCache.remember(event.integrationId(), event.businessKey(), event.hash());
            return;
        }
        seenRecordRepository.save(new SeenRecord(event.integrationId(), event.businessKey(), event.hash()));
        dedupCache.remember(event.integrationId(), event.businessKey(), event.hash());
    }

    private boolean isKnown(Long integrationId, String businessKey) {
        if (dedupCache.isKnown(integrationId, businessKey)) {
            return true;
        }
        Optional<SeenRecord> existing =
                seenRecordRepository.findByIntegrationIdAndBusinessKey(integrationId, businessKey);
        if (existing.isPresent()) {
            // aquece o cache com o hash ja conhecido para acelerar os proximos polls
            dedupCache.remember(integrationId, businessKey, existing.get().getHash());
            return true;
        }
        return false;
    }
}
