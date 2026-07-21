package com.promagroup.apibridge.client;

import com.promagroup.apibridge.entity.AuthType;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Aplica o cabecalho de autenticacao a uma requisicao, conforme o {@link AuthType}.
 *
 * <ul>
 *   <li>{@code BEARER}  -> {@code Authorization: Bearer <secret>}</li>
 *   <li>{@code API_KEY} -> {@code X-API-Key: <secret>} (nome do header configuravel no futuro)</li>
 *   <li>{@code BASIC}   -> {@code Authorization: Basic base64(secret)} (secret no formato user:pass)</li>
 *   <li>{@code OAUTH2}  -> ainda nao implementado (fluxo client-credentials fica para fase futura)</li>
 * </ul>
 */
public final class AuthHeaderFactory {

    private AuthHeaderFactory() {
    }

    public static void apply(HttpHeaders headers, AuthType type, String secret) {
        if (type == null || type == AuthType.NONE || secret == null) {
            return;
        }
        switch (type) {
            case BEARER -> headers.setBearerAuth(secret);
            case API_KEY -> headers.set("X-API-Key", secret);
            case BASIC -> {
                String encoded = Base64.getEncoder()
                        .encodeToString(secret.getBytes(StandardCharsets.UTF_8));
                headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
            }
            case OAUTH2 -> throw new UnsupportedOperationException(
                    "Autenticacao OAUTH2 ainda nao implementada (fluxo client-credentials = fase futura)");
            default -> {
                // NONE ja tratado acima
            }
        }
    }
}
