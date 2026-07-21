package com.promagroup.apibridge.client;

import org.springframework.stereotype.Component;

import java.util.function.UnaryOperator;

/**
 * Resolve o {@code secretRef} de uma integracao para o segredo real.
 *
 * <p>Formato {@code env:NOME} le a variavel de ambiente {@code NOME}. Assim o token nunca
 * fica cru no banco/logs (requisito de seguranca). Um valor sem prefixo e tratado como literal
 * (aceitavel so em dev). O lookup e injetavel para testes.
 */
@Component
public class SecretResolver {

    private static final String ENV_PREFIX = "env:";

    private final UnaryOperator<String> environmentLookup;

    public SecretResolver() {
        this(System::getenv);
    }

    SecretResolver(UnaryOperator<String> environmentLookup) {
        this.environmentLookup = environmentLookup;
    }

    /** @return o segredo resolvido, ou {@code null} se {@code secretRef} for vazio */
    public String resolve(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) {
            return null;
        }
        if (secretRef.startsWith(ENV_PREFIX)) {
            String name = secretRef.substring(ENV_PREFIX.length());
            String value = environmentLookup.apply(name);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Variavel de ambiente nao definida: " + name);
            }
            return value;
        }
        return secretRef;
    }
}
