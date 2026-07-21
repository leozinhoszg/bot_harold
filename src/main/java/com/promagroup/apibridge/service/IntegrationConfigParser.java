package com.promagroup.apibridge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.promagroup.apibridge.dto.IntegrationConfig;
import com.promagroup.apibridge.entity.Integration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Desserializa {@code integrations.config_json} para {@link IntegrationConfig}. */
@Component
public class IntegrationConfigParser {

    private static final Logger log = LoggerFactory.getLogger(IntegrationConfigParser.class);

    private final ObjectMapper objectMapper;

    public IntegrationConfigParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** @return a config, ou {@code null} se ausente/invalida (com log de aviso) */
    public IntegrationConfig parse(Integration integration) {
        String json = integration.getConfigJson();
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, IntegrationConfig.class);
        } catch (Exception e) {
            log.warn("config_json invalido para integracao '{}': {}", integration.getName(), e.getMessage());
            return null;
        }
    }
}
