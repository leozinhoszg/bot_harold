package com.promagroup.apibridge.client;

/**
 * Falha ao chamar uma API externa. {@code httpCode == 0} indica erro sem resposta
 * (timeout/conexao). {@code retryable} sinaliza se a causa era passivel de retry.
 */
public class ApiClientException extends RuntimeException {

    private final int httpCode;
    private final boolean retryable;

    public ApiClientException(int httpCode, String message, boolean retryable) {
        super(message);
        this.httpCode = httpCode;
        this.retryable = retryable;
    }

    public int getHttpCode() {
        return httpCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
