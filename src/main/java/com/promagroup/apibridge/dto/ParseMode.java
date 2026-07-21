package com.promagroup.apibridge.dto;

/**
 * Modo de formatacao da mensagem no Telegram.
 *
 * <p>{@code apiValue} e o valor enviado no parametro {@code parse_mode} da Bot API
 * ({@code null} = enviar sem parse_mode, texto puro).
 */
public enum ParseMode {

    HTML("HTML"),
    MARKDOWN_V2("MarkdownV2"),
    NONE(null);

    private final String apiValue;

    ParseMode(String apiValue) {
        this.apiValue = apiValue;
    }

    public String apiValue() {
        return apiValue;
    }
}
