package com.promagroup.apibridge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.promagroup.apibridge.dto.IntegrationConfig;
import com.promagroup.apibridge.entity.AuthType;
import com.promagroup.apibridge.entity.Integration;
import com.promagroup.apibridge.repository.IntegrationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que o IntegrationSeeder insere as integracoes declaradas em
 * {@code app.seed.integrations} e serializa o config-driven em config_json.
 */
@SpringBootTest
@ActiveProfiles("seedtest")
class IntegrationSeederTest {

    @Autowired
    private IntegrationRepository repository;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void seedsIntegrationFromYaml() throws Exception {
        Integration seeded = repository.findByName("seed-demo").orElseThrow();

        assertThat(seeded.getAuthType()).isEqualTo(AuthType.API_KEY);
        assertThat(seeded.getSecretRef()).isEqualTo("env:DEMO_KEY");
        assertThat(seeded.isEnabled()).isTrue();

        IntegrationConfig config = objectMapper.readValue(seeded.getConfigJson(), IntegrationConfig.class);
        assertThat(config.recordsPath()).isEqualTo("$.items[*]");
        assertThat(config.businessKey()).isEqualTo("$.id");
        assertThat(config.fields()).containsEntry("title", "$.name");
    }
}
