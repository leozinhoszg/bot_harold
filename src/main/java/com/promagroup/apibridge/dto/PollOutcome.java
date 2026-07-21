package com.promagroup.apibridge.dto;

/**
 * Resultado de uma execucao de polling, para logging estruturado e testes.
 *
 * @param integration nome da integracao
 * @param newEvents   registros novos detectados
 * @param sent        notificacoes enviadas com sucesso
 * @param failed      notificacoes que falharam no envio
 * @param skipped     true se o poll foi pulado (lock ja em uso)
 */
public record PollOutcome(String integration, int newEvents, int sent, int failed, boolean skipped) {

    public static PollOutcome skipped(String integration) {
        return new PollOutcome(integration, 0, 0, 0, true);
    }

    public static PollOutcome empty(String integration) {
        return new PollOutcome(integration, 0, 0, 0, false);
    }
}
