package com.promagroup.apibridge.telegram;

/**
 * Falha ao publicar no Telegram. {@code retryable} indica se a causa era transitoria
 * (5xx/429/timeout) — usado para decidir reenvio.
 */
public class TelegramException extends RuntimeException {

    private final boolean retryable;

    public TelegramException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
