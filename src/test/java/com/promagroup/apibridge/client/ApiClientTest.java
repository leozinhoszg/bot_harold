package com.promagroup.apibridge.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.promagroup.apibridge.dto.ApiResponse;
import com.promagroup.apibridge.entity.AuthType;
import com.promagroup.apibridge.entity.Integration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes do ApiClient contra um servidor HTTP falso (MockWebServer), sem contexto Spring:
 * cobre fetch simples, auth, retry em 5xx, nao-retry em 4xx, timeout e paginacao.
 */
class ApiClientTest {

    private MockWebServer server;
    private ApiClient apiClient;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        // responseTimeout generoso (2s) evita flakiness de conexao fria sob carga;
        // o teste de timeout usa um client dedicado de timeout curto.
        apiClient = buildClient(new ApiClientProperties(
                Duration.ofSeconds(2), Duration.ofSeconds(2), 2, Duration.ofMillis(100), 1_048_576));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private ApiClient buildClient(ApiClientProperties props) {
        SecretResolver secretResolver = new SecretResolver(Map.of("TOKEN", "abc")::get);
        return new ApiClient(WebClient.builder(), secretResolver, props, new ObjectMapper());
    }

    private Integration integration(AuthType authType, String secretRef, String path, String configJson) {
        Integration i = new Integration();
        i.setName("test");
        i.setUrl(server.url(path).toString());
        i.setAuthType(authType);
        i.setSecretRef(secretRef);
        i.setCron("0 * * * * *");
        i.setConfigJson(configJson != null ? configJson : "{}");
        return i;
    }

    @Test
    void fetchReturnsBodyStatusAndHeaders() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("X-Trace", "t-1")
                .setBody("{\"ok\":true}"));

        ApiResponse<String> response = apiClient.fetch(integration(AuthType.NONE, null, "/items", null));

        assertThat(response.httpCode()).isEqualTo(200);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.payload()).isEqualTo("{\"ok\":true}");
        assertThat(response.headers()).containsEntry("X-Trace", "t-1");
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void fetchAppliesBearerAuthFromEnvSecret() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

        apiClient.fetch(integration(AuthType.BEARER, "env:TOKEN", "/items", null));

        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer abc");
    }

    @Test
    void fetchRetriesOnServerErrorThenSucceeds() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("recovered"));

        ApiResponse<String> response = apiClient.fetch(integration(AuthType.NONE, null, "/items", null));

        assertThat(response.payload()).isEqualTo("recovered");
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void fetchDoesNotRetryOnClientError() {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("nope"));

        assertThatThrownBy(() -> apiClient.fetch(integration(AuthType.NONE, null, "/items", null)))
                .isInstanceOf(ApiClientException.class)
                .extracting(e -> ((ApiClientException) e).getHttpCode())
                .isEqualTo(404);

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void fetchTimesOutAndFailsAsRetryable() {
        // Client dedicado: responseTimeout curto (400ms) e 1 retry -> rapido e deterministico.
        ApiClient slowClient = buildClient(new ApiClientProperties(
                Duration.ofSeconds(2), Duration.ofMillis(400), 1, Duration.ofMillis(50), 1_048_576));
        // corpo atrasado 3s dispara timeout na tentativa inicial e no retry.
        server.enqueue(new MockResponse().setBody("late").setBodyDelay(3, TimeUnit.SECONDS));
        server.enqueue(new MockResponse().setBody("late").setBodyDelay(3, TimeUnit.SECONDS));

        assertThatThrownBy(() -> slowClient.fetch(integration(AuthType.NONE, null, "/slow", null)))
                .isInstanceOf(ApiClientException.class)
                .extracting(e -> ((ApiClientException) e).isRetryable())
                .isEqualTo(true);
    }

    @Test
    void fetchAllPagesFollowsNextUrlUntilAbsent() {
        String page2Url = server.url("/page2").toString();
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"data\":[1],\"next\":\"" + page2Url + "\"}"));
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"data\":[2]}"));

        String config = "{\"recordsPath\":\"$.data[*]\",\"pagination\":{\"nextUrlPath\":\"$.next\",\"maxPages\":5}}";
        List<ApiResponse<String>> pages =
                apiClient.fetchAllPages(integration(AuthType.NONE, null, "/page1", config));

        assertThat(pages).hasSize(2);
        assertThat(pages.get(0).payload()).contains("\"data\":[1]");
        assertThat(pages.get(1).payload()).contains("\"data\":[2]");
        assertThat(server.getRequestCount()).isEqualTo(2);
    }
}
