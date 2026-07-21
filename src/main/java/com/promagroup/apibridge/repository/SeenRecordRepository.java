package com.promagroup.apibridge.repository;

import com.promagroup.apibridge.entity.SeenRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SeenRecordRepository extends JpaRepository<SeenRecord, Long> {

    /** Deduplicacao rapida: este registro ja foi notificado para esta integracao? */
    boolean existsByIntegrationIdAndBusinessKey(Long integrationId, String businessKey);

    /** Recupera o registro visto (para comparar hash e detectar mudanca de conteudo). */
    Optional<SeenRecord> findByIntegrationIdAndBusinessKey(Long integrationId, String businessKey);
}
