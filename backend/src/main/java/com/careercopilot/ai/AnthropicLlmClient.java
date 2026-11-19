package com.careercopilot.ai;

import java.time.Duration;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Anthropic Messages API over RestClient. Prompts and replies are never logged, and failures carry fixed messages.
 * Created only when ANTHROPIC_API_KEY is set (see AiConfig).
 */
public class AnthropicLlmClient implements LlmClient {

    static final String URL = "https://api.anthropic.com/v1/messages";
    static final String VERSION = "2023-06-01";

    private final AiProperties properties;
    private final RestClient client;
    private final Duration backoff;
    private final ObjectMapper mapper = JsonMapper.builder().build();

    public AnthropicLlmClient(AiProperties properties) {
        this(properties, AiHttp.builder(properties.timeout()), AiHttp.DEFAULT_BACKOFF);
    }

    /** Tests pass a builder bound to a mock server and no backoff. */
    AnthropicLlmClient(AiProperties properties, RestClient.Builder builder, Duration backoff) {
        this.properties = properties;
        this.client = builder.build();
        this.backoff = backoff;
    }

    @Override
    public String complete(String system, String user, int maxTokens) {
        ObjectNode request = mapper.createObjectNode();
        request.put("model", properties.anthropicModel());
        request.put("max_tokens", maxTokens > 0 ? maxTokens : properties.anthropicMaxTokens());
        if (system != null && !system.isBlank()) {
            request.put("system", AiHttp.truncate(system, properties.maxInputChars()));
        }
        ArrayNode messages = request.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        message.put("content", AiHttp.truncate(user, properties.maxInputChars()));

        String body = AiHttp.postJson(client, "Anthropic", URL, headers -> {
            headers.set("x-api-key", properties.anthropicApiKey());
            headers.set("anthropic-version", VERSION);
        }, request.toString(), backoff);
        return extractText(body);
    }

    private String extractText(String body) {
        try {
            StringBuilder text = new StringBuilder();
            for (JsonNode block : mapper.readTree(body).path("content")) {
                if ("text".equals(block.path("type").asString(""))) {
                    text.append(block.path("text").asString(""));
                }
            }
            if (text.isEmpty()) {
                throw new AiProviderException("The AI provider returned an unexpected response.", 0);
            }
            return text.toString();
        } catch (JacksonException | NullPointerException | IllegalArgumentException e) {
            throw new AiProviderException("The AI provider returned an unexpected response.", 0);
        }
    }

    @Override
    public String toString() {
        return "AnthropicLlmClient[model=" + properties.anthropicModel() + "]";
    }
}
