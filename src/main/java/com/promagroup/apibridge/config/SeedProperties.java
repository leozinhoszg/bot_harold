package com.promagroup.apibridge.config;

import com.promagroup.apibridge.dto.IntegrationConfig;
import com.promagroup.apibridge.entity.AuthType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Integracoes declaradas em {@code app.seed.integrations} (YAML), inseridas no boot pelo
 * {@link IntegrationSeeder}. Permite provisionar APIs sem escrever no banco manualmente.
 */
@ConfigurationProperties(prefix = "app.seed")
public record SeedProperties(List<IntegrationSeed> integrations) {

    public record IntegrationSeed(
            String name,
            String url,
            boolean enabled,
            String cron,
            AuthType authType,
            String secretRef,
            String chatId,
            IntegrationConfig config
    ) {
    }
}
