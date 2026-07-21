package com.promagroup.apibridge.service;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lock em memoria por integracao: garante que um poll nao se sobreponha ao proximo tick do
 * scheduler para a mesma integracao. Suficiente para instancia unica; a evolucao para lock
 * distribuido (Redis) fica para quando houver multiplas instancias.
 */
@Component
public class PollingLock {

    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    /** @return true se adquiriu o lock; false se ja havia um poll em andamento para esta integracao */
    public boolean tryAcquire(Long integrationId) {
        return running.add(integrationId);
    }

    public void release(Long integrationId) {
        running.remove(integrationId);
    }
}
