package com.promagroup.apibridge.repository;

import com.promagroup.apibridge.entity.AuthType;
import com.promagroup.apibridge.entity.Integration;
import com.promagroup.apibridge.entity.Notification;
import com.promagroup.apibridge.entity.NotificationStatus;
import com.promagroup.apibridge.entity.SeenRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes de persistencia da Fase 1: mapeamento das entidades e queries derivadas
 * sobre SQLite (via Liquibase). Transacional -> rollback ao fim de cada teste.
 */
@SpringBootTest
@Transactional
class RepositoryTest {

    @Autowired
    private IntegrationRepository integrationRepository;
    @Autowired
    private SeenRecordRepository seenRecordRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    private static Integration integration(String name, boolean enabled) {
        Integration i = new Integration();
        i.setName(name);
        i.setUrl("https://api.exemplo.com/items");
        i.setEnabled(enabled);
        i.setCron("*/30 * * * * *");
        i.setAuthType(AuthType.BEARER);
        i.setSecretRef("env:EXEMPLO_TOKEN");
        i.setChatId("-100123");
        i.setConfigJson("{\"recordsPath\":\"$.data[*]\"}");
        return i;
    }

    @Test
    void integration_isPersistedAndFoundByName() {
        Integration saved = integrationRepository.save(integration("acme", true));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        Integration found = integrationRepository.findByName("acme").orElseThrow();
        assertThat(found.getAuthType()).isEqualTo(AuthType.BEARER);
        assertThat(found.getSecretRef()).isEqualTo("env:EXEMPLO_TOKEN");
        assertThat(found.getConfigJson()).contains("recordsPath");
    }

    @Test
    void findByEnabledTrue_returnsOnlyEnabled() {
        integrationRepository.save(integration("ativa", true));
        integrationRepository.save(integration("inativa", false));

        List<Integration> enabled = integrationRepository.findByEnabledTrue();

        assertThat(enabled).extracting(Integration::getName).contains("ativa").doesNotContain("inativa");
    }

    @Test
    void seenRecord_existsByIntegrationIdAndBusinessKey() {
        seenRecordRepository.save(new SeenRecord(7L, "item-42", "hash-abc"));

        assertThat(seenRecordRepository.existsByIntegrationIdAndBusinessKey(7L, "item-42")).isTrue();
        assertThat(seenRecordRepository.existsByIntegrationIdAndBusinessKey(7L, "outro")).isFalse();
        assertThat(seenRecordRepository.findByIntegrationIdAndBusinessKey(7L, "item-42"))
                .get().extracting(SeenRecord::getHash).isEqualTo("hash-abc");
    }

    @Test
    void seenRecord_uniqueConstraint_blocksDuplicateBusinessKey() {
        seenRecordRepository.saveAndFlush(new SeenRecord(1L, "dup", "h1"));

        // SQLite bloqueia a duplicata; o Spring pode traduzir para JpaSystemException ou
        // DataIntegrityViolationException (ambas DataAccessException). Verificamos o bloqueio.
        assertThatThrownBy(() -> seenRecordRepository.saveAndFlush(new SeenRecord(1L, "dup", "h2")))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("UNIQUE constraint failed");
    }

    @Test
    void notification_persistsWithStatusAndFindsByIntegration() {
        Notification n = new Notification(5L, "item-1", "*Ola*");
        n.setStatus(NotificationStatus.SENT);
        n.setTelegramMessageId("999");
        notificationRepository.save(n);

        List<Notification> found = notificationRepository.findByIntegrationId(5L);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(found.get(0).getTelegramMessageId()).isEqualTo("999");
    }
}
