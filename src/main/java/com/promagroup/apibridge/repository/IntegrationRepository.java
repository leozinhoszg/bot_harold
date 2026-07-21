package com.promagroup.apibridge.repository;

import com.promagroup.apibridge.entity.Integration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IntegrationRepository extends JpaRepository<Integration, Long> {

    /** Integracoes ativas — usadas pelo scheduler dinamico para registrar os polls. */
    List<Integration> findByEnabledTrue();

    /** Lookup por nome (unico) — usado pelo seeder para upsert idempotente. */
    Optional<Integration> findByName(String name);
}
