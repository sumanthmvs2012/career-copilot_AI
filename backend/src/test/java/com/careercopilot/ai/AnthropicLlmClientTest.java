package com.careercopilot.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The SDK-based client against a local fake of the Messages API (no network, no real key). */
class AnthropicLlmClientTest {

    record Answer(String value) {
    }

    /** Shaped like a real API key; the SDK sends such keys as x-api-key. */
    private static final String API_KEY = "sk-ant-api03-test-secret-key";

    private final JsonMapper mapper = JsonMapper.builder().build();
    private HttpServer server;
    private final List<String> bodies = new ArrayList<>();
    private final Map<String, String> headers = new ConcurrentHashMap<>();
    private final AtomicInteger hits = new AtomicInteger();
    private volatile int status = 200;
    private volatile String reply = message("end_turn", "{\"value\":\"hello\"}");

    private static String message(String stopReason, String text) {
        String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-opus-5-5\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"" + escaped + "\"}],"
                + "\"stop_reason\":\"" + stopReason + "\",\"stop_sequence\":null,"
                + "\"usage\":{\"input_tokens\":10,\"output_tokens\":5}}";
    }

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            hits.incrementAndGet();
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), String.join(",", values)));
            byte[] out = (status == 200 ? reply : "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\","
                    + "\"message\":\"bad\"}}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, out.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(out);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private AnthropicLlmClient client(boolean fallbacks) {
        AiProperties properties = new AiProperties(API_KEY, "claude-opus-5-5", 16000, "medium",
                fallbacks, Duration.ofSeconds(10), "", "voyage-3.5-lite", 50, 20000, Duration.ofSeconds(5), 0.3, 0.8);
        return new AnthropicLlmClient(properties, "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @Test
    void sendsAStructuredRequestAndParsesTheReply() {
        Answer answer = client(true).completeStructured("Be brief.", "Say hello.", Answer.class, 0);

        assertThat(answer).isEqualTo(new Answer("hello"));
        JsonNode request = mapper.readTree(bodies.get(0));
        assertThat(request.path("model").asString()).isEqualTo("claude-opus-5-5");
        assertThat(request.path("max_tokens").asInt()).isEqualTo(16000);
        assertThat(request.path("output_config").path("effort").asString()).isEqualTo("medium");
        assertThat(request.path("output_config").path("format").path("type").asString()).isEqualTo("json_schema");
        assertThat(request.path("output_config").path("format").path("schema").path("properties").has("value")).isTrue();
        assertThat(request.path("system").asString()).isEqualTo("Be brief.");
        assertThat(request.path("messages").get(0).path("content").asString()).isEqualTo("Say hello.");
        assertThat(request.has("thinking")).isFalse();
        assertThat(request.path("fallbacks").asString()).isEqualTo("default");
        assertThat(headers.get("anthropic-beta")).contains(AnthropicLlmClient.FALLBACK_BETA);
        assertThat(headers.get("x-api-key")).isEqualTo(API_KEY);
        assertThat(headers).doesNotContainKey("authorization");
    }

    @Test
    void fallbacksCanBeSwitchedOff() {
        client(false).completeStructured("s", "u", Answer.class, 500);

        JsonNode request = mapper.readTree(bodies.get(0));
        assertThat(request.has("fallbacks")).isFalse();
        assertThat(request.path("max_tokens").asInt()).isEqualTo(500);
        assertThat(headers.getOrDefault("anthropic-beta", "")).doesNotContain(AnthropicLlmClient.FALLBACK_BETA);
    }

    @Test
    void aRefusalIsNotUsed() {
        reply = message("refusal", "");

        assertThatThrownBy(() -> client(true).completeStructured("s", "u", Answer.class, 0))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("The AI provider did not return a usable answer.");
    }

    @Test
    void aCutOffReplyIsNotUsed() {
        reply = message("max_tokens", "{\"value\":\"hel");

        assertThatThrownBy(() -> client(true).completeStructured("s", "u", Answer.class, 0))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("The AI provider did not return a usable answer.");
    }

    @Test
    void anErrorStatusBecomesAFixedMessageWithoutTheKey() {
        status = 400;

        assertThatThrownBy(() -> client(true).completeStructured("s", "u", Answer.class, 0))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("The AI provider request failed.")
                .satisfies(e -> assertThat(((AiProviderException) e).status()).isEqualTo(400))
                .satisfies(e -> assertThat(e.toString()).doesNotContain(API_KEY));
        // 400 is not retried.
        assertThat(hits.get()).isEqualTo(1);
    }

    @Test
    void anUnreachableServerBecomesAFixedMessage() {
        AnthropicLlmClient client = client(true);
        server.stop(0);

        assertThatThrownBy(() -> client.completeStructured("s", "u", Answer.class, 0))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("The AI provider could not be reached.");
    }

    @Test
    void toStringNeverShowsTheKey() {
        assertThat(client(true).toString()).doesNotContain(API_KEY).contains("claude-opus-5-5");
    }
}
