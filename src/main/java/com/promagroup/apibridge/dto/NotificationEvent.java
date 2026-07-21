package com.promagroup.apibridge.dto;

import java.util.Map;

/**
 * Evento gerado pela Processing Engine para um registro novo que deve virar notificacao.
 * Independente do Telegram: a formatacao do template acontece na camada de publicacao.
 *
 * @param integrationId integracao de origem
 * @param businessKey   chave de deduplicacao
 * @param hash          hash de conteudo (persistido em seen_records apos o envio)
 * @param fields        valores para renderizar o template
 */
public record NotificationEvent(
        Long integrationId,
        String businessKey,
        String hash,
        Map<String, String> fields
) {
}
