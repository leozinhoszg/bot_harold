package com.promagroup.apibridge.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.promagroup.apibridge.dto.ApiResponse;
import com.promagroup.apibridge.dto.IntegrationConfig;
import com.promagroup.apibridge.entity.Integration;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Executa chamadas HTTP GET a APIs externas com autenticacao, timeout, retry com backoff
 * exponencial e paginacao dirigida pela resposta. Retorna JSON cru em {@link ApiResponse}.
 *
 * <p>O metodo e sincrono ({@code block}) porque o consumidor (scheduler/engine) e sincrono;
 * nao ha servidor reativo neste servico.
 */
@Component
@EnableConfigurationProperties(ApiClientProperties.class)
public class ApiClient {

    private static final Logger log = LoggerFactory.getLogger(ApiClient.class);

    private final WebClient webClient;
    private final SecretResolver secretResolver;
    private final ApiClientProperties properties;
    private final ObjectMapper objectMapper;

    public ApiClient(WebClient.Builder webClientBuilder,
                     SecretResolver secretResolver,
                     ApiClientProperties properties,
                     ObjectMapper objectMapper) {
        this.secretResolver = secretResolver;
        this.properties = properties;
        this.objectMapper = objectMapper;

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) properties.connectTimeout().toMillis())
                // Backstop no nivel de rede; o timeout autoritativo e o .timeout() do Mono,
                // que emite java.util.concurrent.TimeoutException (tratado como retryable).
                .responseTimeout(properties.responseTimeout().plusSeconds(5));

        this.webClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(properties.maxInMemoryBytes()))
                .build();
    }

    /** Busca a URL base da integracao (pagina unica). */
    public ApiResponse<String> fetch(Integration integration) {
        return fetchUrl(integration, integration.getUrl());
    }

    /**
     * Busca todas as paginas seguindo {@code pagination.nextUrlPath} da config, ate o limite
     * {@code maxPages}. Sem config de paginacao, retorna a pagina unica.
     */
    public List<ApiResponse<String>> fetchAllPages(Integration integration) {
        IntegrationConfig config = parseConfig(integration);
        IntegrationConfig.PaginationConfig pagination = config != null ? config.pagination() : null;
        int maxPages = resolveMaxPages(pagination);

        List<ApiResponse<String>> pages = new ArrayList<>();
        String url = integration.getUrl();
        for (int page = 0; page < maxPages && url != null; page++) {
            ApiResponse<String> response = fetchUrl(integration, url);
            pages.add(response);
            url = (pagination != null && pagination.nextUrlPath() != null)
                    ? extractNextUrl(url, response.payload(), pagination.nextUrlPath())
                    : null;
        }
        return pages;
    }

    private ApiResponse<String> fetchUrl(Integration integration, String url) {
        String secret = secretResolver.resolve(integration.getSecretRef());
        try {
            ResponseEntity<String> entity = webClient.get()
                    .uri(URI.create(url))
                    .headers(headers -> AuthHeaderFactory.apply(headers, integration.getAuthType(), secret))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, resp ->
                            resp.bodyToMono(String.class).defaultIfEmpty("")
                                    .flatMap(body -> Mono.error(new ApiClientException(
                                            resp.statusCode().value(),
                                            "Erro 4xx de " + url + ": " + truncate(body), false))))
                    .toEntity(String.class)
                    .timeout(properties.responseTimeout())
                    .retryWhen(Retry.backoff(properties.retryMaxAttempts(), properties.retryBackoff())
                            .filter(ApiClient::isRetryable))
                    .onErrorMap(Exceptions::isRetryExhausted, t -> new ApiClientException(
                            0, "Falha apos " + properties.retryMaxAttempts() + " retries em " + url
                            + ": " + rootMessage(t), true))
                    .block(properties.responseTimeout().plusSeconds(5));

            return toApiResponse(entity);
        } catch (ApiClientException e) {
            throw e;
        } catch (WebClientResponseException e) {
            throw new ApiClientException(e.getStatusCode().value(),
                    "Erro HTTP de " + url + ": " + e.getMessage(), e.getStatusCode().is5xxServerError());
        }
    }

    private ApiResponse<String> toApiResponse(ResponseEntity<String> entity) {
        return new ApiResponse<>(
                entity.getStatusCode().value(),
                entity.getBody(),
                entity.getHeaders().toSingleValueMap(),
                Instant.now());
    }

    static boolean isRetryable(Throwable t) {
        if (t instanceof ApiClientException e) {
            return e.isRetryable();
        }
        if (t instanceof WebClientResponseException w) {
            return w.getStatusCode().is5xxServerError();
        }
        return t instanceof TimeoutException || t instanceof IOException;
    }

    private int resolveMaxPages(IntegrationConfig.PaginationConfig pagination) {
        if (pagination == null || pagination.maxPages() == null || pagination.maxPages() <= 0) {
            return 1;
        }
        return pagination.maxPages();
    }

    private String extractNextUrl(String currentUrl, String body, String nextUrlPath) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            Object value = JsonPath.read(body, nextUrlPath);
            if (value == null) {
                return null;
            }
            String next = String.valueOf(value);
            if (next.isBlank()) {
                return null;
            }
            // resolve() cobre tanto URL absoluta quanto caminho relativo a atual
            return URI.create(currentUrl).resolve(next).toString();
        } catch (PathNotFoundException e) {
            return null;
        }
    }

    private IntegrationConfig parseConfig(Integration integration) {
        String json = integration.getConfigJson();
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, IntegrationConfig.class);
        } catch (Exception e) {
            log.warn("config_json invalido para integracao '{}': {}", integration.getName(), e.getMessage());
            return null;
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t.getCause() != null ? t.getCause() : t;
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 200 ? body : body.substring(0, 200) + "...";
    }
}
