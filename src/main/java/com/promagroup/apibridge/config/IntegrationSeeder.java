package com.promagroup.apibridge.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promagroup.apibridge.entity.AuthType;
import com.promagroup.apibridge.entity.Integration;
import com.promagroup.apibridge.repository.IntegrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Insere no boot as integracoes declaradas em {@code app.seed.integrations}.
 *
 * <p>Idempotente: pula integracoes cujo {@code name} ja existe. Assim o seed pode ficar no
 * application.yml sem duplicar registros a cada reinicio.
 */
@Component
@EnableConfigurationProperties(SeedProperties.class)
public class IntegrationSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IntegrationSeeder.class);

    private final IntegrationRepository repository;
    private final ObjectMapper objectMapper;
    private final SeedProperties seedProperties;

    public IntegrationSeeder(IntegrationRepository repository,
                             ObjectMapper objectMapper,
                             SeedProperties seedProperties) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.seedProperties = seedProperties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<SeedProperties.IntegrationSeed> seeds = seedProperties.integrations();
        if (seeds == null || seeds.isEmpty()) {
            log.debug("Nenhuma integracao de seed configurada");
            return;
        }
        for (SeedProperties.IntegrationSeed seed : seeds) {
            if (repository.findByName(seed.name()).isPresent()) {
                log.debug("Integracao '{}' ja existe; seed ignorado", seed.name());
                continue;
            }
            repository.save(toEntity(seed));
            log.info("Integracao '{}' inserida via seed", seed.name());
        }
    }

    private Integration toEntity(SeedProperties.IntegrationSeed seed) {
        Integration entity = new Integration();
        entity.setName(seed.name());
        entity.setUrl(seed.url());
        entity.setEnabled(seed.enabled());
        entity.setCron(seed.cron());
        entity.setAuthType(seed.authType() != null ? seed.authType() : AuthType.NONE);
        entity.setSecretRef(seed.secretRef());
        entity.setChatId(seed.chatId());
        entity.setConfigJson(serialize(seed));
        return entity;
    }

    private String serialize(SeedProperties.IntegrationSeed seed) {
        try {
            return objectMapper.writeValueAsString(seed.config());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Falha ao serializar config da integracao de seed '" + seed.name() + "'", e);
        }
    }
}
