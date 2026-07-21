package com.promagroup.apibridge.client;

import com.promagroup.apibridge.entity.AuthType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthHeaderFactoryTest {

    @Test
    void bearerSetsAuthorizationHeader() {
        HttpHeaders headers = new HttpHeaders();
        AuthHeaderFactory.apply(headers, AuthType.BEARER, "abc123");
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer abc123");
    }

    @Test
    void apiKeySetsCustomHeader() {
        HttpHeaders headers = new HttpHeaders();
        AuthHeaderFactory.apply(headers, AuthType.API_KEY, "key-xyz");
        assertThat(headers.getFirst("X-API-Key")).isEqualTo("key-xyz");
    }

    @Test
    void basicEncodesCredentials() {
        HttpHeaders headers = new HttpHeaders();
        AuthHeaderFactory.apply(headers, AuthType.BASIC, "user:pass");
        String expected = "Basic " + Base64.getEncoder()
                .encodeToString("user:pass".getBytes(StandardCharsets.UTF_8));
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo(expected);
    }

    @Test
    void noneAndNullSecretAddNothing() {
        HttpHeaders headers = new HttpHeaders();
        AuthHeaderFactory.apply(headers, AuthType.NONE, "ignored");
        AuthHeaderFactory.apply(headers, AuthType.BEARER, null);
        assertThat(headers).isEmpty();
    }

    @Test
    void oauth2NotYetSupported() {
        HttpHeaders headers = new HttpHeaders();
        assertThatThrownBy(() -> AuthHeaderFactory.apply(headers, AuthType.OAUTH2, "token"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
