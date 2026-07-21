package com.promagroup.apibridge.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Resposta normalizada de uma chamada a API externa.
 *
 * @param httpCode  status HTTP retornado
 * @param payload   corpo da resposta (o cliente retorna JSON cru como {@code String})
 * @param headers   cabecalhos achatados (primeiro valor de cada)
 * @param timestamp instante da resposta
 */
public record ApiResponse<T>(
        int httpCode,
        T payload,
        Map<String, String> headers,
        Instant timestamp
) {

    public boolean isSuccess() {
        return httpCode >= 200 && httpCode < 300;
    }
}
