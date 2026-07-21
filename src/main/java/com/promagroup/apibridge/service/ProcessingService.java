package com.promagroup.apibridge.service;

import com.promagroup.apibridge.dto.ExtractedRecord;
import com.promagroup.apibridge.dto.IntegrationConfig;
import com.promagroup.apibridge.dto.NotificationEvent;
import com.promagroup.apibridge.entity.Integration;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Fachada da Processing Engine: recebe uma integracao e o payload cru e devolve os eventos
 * de notificacao (registros novos). Combina {@link Extractor} + {@link Detector} e e totalmente
 * independente do Telegram.
 */
@Service
public class ProcessingService {

    private final Extractor extractor;
    private final Detector detector;
    private final IntegrationConfigParser configParser;

    public ProcessingService(Extractor extractor, Detector detector, IntegrationConfigParser configParser) {
        this.extractor = extractor;
        this.detector = detector;
        this.configParser = configParser;
    }

    /** Extrai registros do payload e retorna apenas os novos como eventos de notificacao. */
    public List<NotificationEvent> process(Integration integration, String payload) {
        IntegrationConfig config = configParser.parse(integration);
        if (config == null) {
            return List.of();
        }
        List<ExtractedRecord> records = extractor.extract(config, payload);
        return detector.detectNew(integration, records);
    }
}
