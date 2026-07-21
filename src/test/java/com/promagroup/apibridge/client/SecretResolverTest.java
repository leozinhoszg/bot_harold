package com.promagroup.apibridge.client;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretResolverTest {

    private final SecretResolver resolver =
            new SecretResolver(Map.of("MY_TOKEN", "s3cr3t")::get);

    @Test
    void resolvesEnvReference() {
        assertThat(resolver.resolve("env:MY_TOKEN")).isEqualTo("s3cr3t");
    }

    @Test
    void returnsNullForBlankRef() {
        assertThat(resolver.resolve(null)).isNull();
        assertThat(resolver.resolve("  ")).isNull();
    }

    @Test
    void treatsUnprefixedValueAsLiteral() {
        assertThat(resolver.resolve("plain-value")).isEqualTo("plain-value");
    }

    @Test
    void throwsWhenEnvVarMissing() {
        assertThatThrownBy(() -> resolver.resolve("env:UNKNOWN"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UNKNOWN");
    }
}
