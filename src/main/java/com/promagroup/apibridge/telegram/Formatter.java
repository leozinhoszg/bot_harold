package com.promagroup.apibridge.telegram;

import com.promagroup.apibridge.dto.ParseMode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renderiza o template de uma integracao substituindo placeholders {@code {campo}} pelos valores.
 *
 * <p>Principio de seguranca: a marcacao do template e confiavel (escrita pelo operador), mas os
 * VALORES dos campos vem da API externa e sao escapados conforme o {@link ParseMode}. Assim um
 * valor com {@code <}, {@code *} etc. nao quebra a formatacao nem injeta marcacao.
 */
@Component
public class Formatter {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)\\}");

    // Caracteres reservados do MarkdownV2 que precisam de escape com barra invertida.
    private static final String MARKDOWN_V2_SPECIAL = "_*[]()~`>#+-=|{}.!\\";

    public String render(String template, Map<String, String> fields, ParseMode mode) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        ParseMode effective = mode != null ? mode : ParseMode.HTML;
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = (fields != null && fields.containsKey(key))
                    ? escape(fields.get(key), effective)
                    : matcher.group(0); // placeholder desconhecido fica literal (erro visivel ao operador)
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    static String escape(String value, ParseMode mode) {
        if (value == null) {
            return "";
        }
        return switch (mode) {
            case HTML -> value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            case MARKDOWN_V2 -> escapeMarkdownV2(value);
            case NONE -> value;
        };
    }

    private static String escapeMarkdownV2(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (MARKDOWN_V2_SPECIAL.indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
