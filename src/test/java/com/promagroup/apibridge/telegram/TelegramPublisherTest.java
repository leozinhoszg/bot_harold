package com.promagroup.apibridge.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.promagroup.apibridge.dto.ParseMode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelegramPublisherTest {

    private MockWebServer server;
    private TelegramPublisher publisher;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        TelegramProperties props = new TelegramProperties(
                "test-token", null, server.url("/").toString(),
                Duration.ofMillis(500), 2, Duration.ofMillis(50));
        publisher = new TelegramPublisher(WebClient.builder(), props, new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private static MockResponse ok(String json) {
        return new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody(json);
    }

    @Test
    void sendMessageReturnsMessageIdAndPostsCorrectPayload() throws InterruptedException {
        server.enqueue(ok("{\"ok\":true,\"result\":{\"message_id\":123}}"));

        String messageId = publisher.sendMessage("-100999", "<b>Oi</b>", ParseMode.HTML);

        assertThat(messageId).isEqualTo("123");
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/bottest-token/sendMessage");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"chat_id\":\"-100999\"")
                .contains("\"parse_mode\":\"HTML\"")
                .contains("\"text\":\"<b>Oi</b>\"");
    }

    @Test
    void okFalseBecomesNonRetryableException() {
        server.enqueue(ok("{\"ok\":false,\"description\":\"chat not found\"}"));

        assertThatThrownBy(() -> publisher.sendMessage("bad", "x", ParseMode.HTML))
                .isInstanceOf(TelegramException.class)
                .hasMessageContaining("chat not found");
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void retriesOnServerErrorThenSucceeds() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));
        server.enqueue(ok("{\"ok\":true,\"result\":{\"message_id\":7}}"));

        String messageId = publisher.sendMessage("c", "x", ParseMode.HTML);

        assertThat(messageId).isEqualTo("7");
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void retriesOnTooManyRequestsThenSucceeds() {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("{\"ok\":false,\"description\":\"Too Many Requests\"}"));
        server.enqueue(ok("{\"ok\":true,\"result\":{\"message_id\":8}}"));

        assertThat(publisher.sendMessage("c", "x", ParseMode.HTML)).isEqualTo("8");
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void doesNotRetryOnBadRequest() {
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"ok\":false,\"description\":\"Bad Request: message text is empty\"}"));

        assertThatThrownBy(() -> publisher.sendMessage("c", "", ParseMode.HTML))
                .isInstanceOf(TelegramException.class)
                .hasMessageContaining("message text is empty");
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void deleteMessageSucceeds() throws InterruptedException {
        server.enqueue(ok("{\"ok\":true,\"result\":true}"));

        publisher.deleteMessage("-100999", "123");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/bottest-token/deleteMessage");
        assertThat(request.getBody().readUtf8()).contains("\"message_id\":\"123\"");
    }
}
