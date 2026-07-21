package com.promagroup.apibridge.telegram;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promagroup.apibridge.dto.ParseMode;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Publica mensagens via Telegram Bot API (sendMessage/editMessageText/deleteMessage).
 *
 * <p>Retry com backoff exponencial em erros transitorios (5xx, 429, timeout). Erros logicos
 * (resposta {@code ok=false}) e 4xx nao-429 nao sao retentados. Nunca loga o token.
 */
@Component
@EnableConfigurationProperties(TelegramProperties.class)
public class TelegramPublisher {

    private static final Logger log = LoggerFactory.getLogger(TelegramPublisher.class);

    private final WebClient webClient;
    private final TelegramProperties properties;
    private final ObjectMapper objectMapper;

    public TelegramPublisher(WebClient.Builder webClientBuilder,
                             TelegramProperties properties,
                             ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(properties.timeout().plusSeconds(5));
        this.webClient = webClientBuilder
                .baseUrl(properties.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /** Envia uma mensagem e retorna o {@code message_id} do Telegram. */
    public String sendMessage(String chatId, String text, ParseMode parseMode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        if (parseMode != null && parseMode.apiValue() != null) {
            body.put("parse_mode", parseMode.apiValue());
        }
        body.put("disable_web_page_preview", true);

        JsonNode result = callMethod("sendMessage", body);
        return result.path("result").path("message_id").asText();
    }

    /** Edita o texto de uma mensagem existente. */
    public void editMessage(String chatId, String messageId, String text, ParseMode parseMode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("text", text);
        if (parseMode != null && parseMode.apiValue() != null) {
            body.put("parse_mode", parseMode.apiValue());
        }
        callMethod("editMessageText", body);
    }

    /** Remove uma mensagem. */
    public void deleteMessage(String chatId, String messageId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        callMethod("deleteMessage", body);
    }

    private JsonNode callMethod(String method, Map<String, Object> body) {
        String path = "/bot" + properties.token() + "/" + method;
        try {
            ResponseEntity<String> entity = webClient.post()
                    .uri(path)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(status -> status.value() == 429 || status.is5xxServerError(), resp ->
                            resp.bodyToMono(String.class).defaultIfEmpty("")
                                    .flatMap(b -> Mono.error(new TelegramException(
                                            "Telegram " + method + " transitorio " + resp.statusCode()
                                                    + ": " + describe(b), true))))
                    .onStatus(HttpStatusCode::is4xxClientError, resp ->
                            resp.bodyToMono(String.class).defaultIfEmpty("")
                                    .flatMap(b -> Mono.error(new TelegramException(
                                            "Telegram " + method + " 4xx " + resp.statusCode()
                                                    + ": " + describe(b), false))))
                    .toEntity(String.class)
                    .timeout(properties.timeout())
                    .retryWhen(Retry.backoff(properties.retryMaxAttempts(), properties.retryBackoff())
                            .filter(TelegramPublisher::isRetryable))
                    .onErrorMap(Exceptions::isRetryExhausted, t -> new TelegramException(
                            "Telegram " + method + " falhou apos " + properties.retryMaxAttempts()
                                    + " retries: " + rootMessage(t), true))
                    .block(properties.timeout().plusSeconds(5));

            JsonNode node = objectMapper.readTree(entity != null ? entity.getBody() : "");
            if (!node.path("ok").asBoolean(false)) {
                throw new TelegramException(
                        "Telegram " + method + " respondeu ok=false: " + node.path("description").asText(), false);
            }
            log.debug("Telegram {} ok", method);
            return node;
        } catch (TelegramException e) {
            throw e;
        } catch (WebClientResponseException e) {
            throw new TelegramException("Telegram " + method + " erro HTTP: " + e.getStatusCode(),
                    e.getStatusCode().is5xxServerError() || e.getStatusCode().value() == 429);
        } catch (JsonProcessingException e) {
            throw new TelegramException("Telegram " + method + " retornou corpo invalido", false);
        }
    }

    static boolean isRetryable(Throwable t) {
        if (t instanceof TelegramException e) {
            return e.isRetryable();
        }
        if (t instanceof WebClientResponseException w) {
            return w.getStatusCode().is5xxServerError() || w.getStatusCode().value() == 429;
        }
        return t instanceof TimeoutException || t instanceof IOException;
    }

    /** Extrai o campo {@code description} do corpo de erro do Telegram, se houver. */
    private String describe(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            String description = node.path("description").asText(null);
            return description != null ? description : truncate(body);
        } catch (JsonProcessingException e) {
            return truncate(body);
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t.getCause() != null ? t.getCause() : t;
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    private static String truncate(String body) {
        return body.length() <= 200 ? body : body.substring(0, 200) + "...";
    }
}
