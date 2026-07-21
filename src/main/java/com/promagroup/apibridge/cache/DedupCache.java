package com.promagroup.apibridge.cache;

/**
 * Cache de deduplicacao. Otimizacao sobre o SQLite (fonte da verdade): evita ler o banco
 * quando a chave ja e conhecida. Implementacoes devem degradar graciosamente — nunca lancar —
 * para que a queda do cache nao derrube o polling.
 */
public interface DedupCache {

    /** @return true se a chave e conhecida no cache; false em miss OU em qualquer falha do cache */
    boolean isKnown(Long integrationId, String businessKey);

    /** Registra a chave como conhecida. Falhas sao engolidas (best-effort). */
    void remember(Long integrationId, String businessKey, String hash);
}
