package com.promagroup.apibridge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Registro ja notificado, por integracao. Fonte da verdade da deduplicacao.
 *
 * <p>Chave de unicidade: (integrationId, businessKey). O {@code hash} cobre o conteudo dos campos,
 * permitindo detectar quando um registro conhecido mudou (evolucao futura).
 */
@Entity
@Table(name = "seen_records")
public class SeenRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "integration_id", nullable = false)
    private Long integrationId;

    @Column(name = "business_key", nullable = false)
    private String businessKey;

    @Column(nullable = false)
    private String hash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected SeenRecord() {
        // exigido pelo JPA
    }

    public SeenRecord(Long integrationId, String businessKey, String hash) {
        this.integrationId = integrationId;
        this.businessKey = businessKey;
        this.hash = hash;
    }

    public Long getId() {
        return id;
    }

    public Long getIntegrationId() {
        return integrationId;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
