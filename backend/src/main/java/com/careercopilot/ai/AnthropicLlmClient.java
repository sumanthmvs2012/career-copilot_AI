package com.careercopilot.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonSchemaLocalValidation;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Claude through the official Anthropic Java SDK, with structured outputs (a JSON schema derived from a record).
 * Prompts and replies are never logged; failures carry fixed messages. Created only when ANTHROPIC_API_KEY is set
 * (see AiConfig).
 */
public class AnthropicLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLlmClient.class);

    static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";
    private static final int MAX_RETRIES = 2;

    private final AiProperties properties;
    private final AnthropicClient client;

    public AnthropicLlmClient(AiProperties properties) {
        this(properties, null);
    }

    /** Tests pass the URL of a local fake server. */
    AnthropicLlmClient(AiProperties properties, String baseUrl) {
        this.properties = properties;
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                .apiKey(properties.anthropicApiKey())
                // API keys belong in x-api-key. Without this header, SDK 3.10 sends an explicit credential as
                // "Authorization: Bearer", which is the form for OAuth tokens.
                .putHeader("x-api-key", properties.anthropicApiKey())
                .timeout(properties.anthropicTimeout())
                // The SDK retries 408, 409, 429, 5xx and connection errors with backoff.
                .maxRetries(MAX_RETRIES);
        if (baseUrl != null) {
            builder.baseUrl(baseUrl);
        }
        this.client = builder.build();
    }

    @Override
    public <T> T completeStructured(String system, String user, Class<T> type, int maxTokens) {
        StructuredMessageCreateParams.Builder<T> params = MessageCreateParams.builder()
                .model(properties.anthropicModel())
                .maxTokens(maxTokens > 0 ? maxTokens : properties.anthropicMaxTokens())
                .outputConfig(StructuredOutputConfig.<T>builder()
                        .effort(effort())
                        .format(type, JsonSchemaLocalValidation.YES)
                        .build())
                .system(AiHttp.truncate(system, properties.maxInputChars()))
                .addUserMessage(AiHttp.truncate(user, properties.maxInputChars()));
        if (properties.anthropicFallbacks()) {
            // "default" lets the API pick the fallback model by refusal category; a refused request is retried there.
            params.putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }

        long started = System.nanoTime();
        StructuredMessage<T> message;
        try {
            message = client.messages().create(params.build());
        } catch (AnthropicServiceException e) {
            log.warn("Anthropic call returned status {} after {} ms", e.statusCode(), millisSince(started));
            throw new AiProviderException("The AI provider request failed.", e.statusCode());
        } catch (AnthropicException e) {
            // Connection errors and timeouts: the exception text can carry request details, so only its class is logged.
            log.warn("Anthropic call failed after {} ms: {}", millisSince(started), e.getClass().getSimpleName());
            throw new AiProviderException("The AI provider could not be reached.", 0);
        }

        Optional<StopReason> stop = message.stopReason();
        log.info("Anthropic call finished in {} ms (stop reason {})", millisSince(started),
                stop.map(StopReason::toString).orElse("none"));
        if (stop.isPresent() && (stop.get().equals(StopReason.REFUSAL) || stop.get().equals(StopReason.MAX_TOKENS))) {
            // A refusal has no usable content and a cut-off reply is incomplete JSON.
            throw new AiProviderException("The AI provider did not return a usable answer.", 0);
        }
        try {
            return message.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(text -> text.text())
                    .findFirst()
                    .orElseThrow(() -> new AiProviderException("The AI provider returned an unexpected response.", 0));
        } catch (AiProviderException e) {
            throw e;
        } catch (RuntimeException e) {
            // The reply did not parse into the requested record.
            log.warn("Anthropic reply could not be read: {}", e.getClass().getSimpleName());
            throw new AiProviderException("The AI provider returned an unexpected response.", 0);
        }
    }

    private OutputConfig.Effort effort() {
        return switch (properties.anthropicEffort().strip().toLowerCase(Locale.ROOT)) {
            case "low" -> OutputConfig.Effort.LOW;
            case "high" -> OutputConfig.Effort.HIGH;
            case "xhigh" -> OutputConfig.Effort.XHIGH;
            case "max" -> OutputConfig.Effort.MAX;
            default -> OutputConfig.Effort.MEDIUM;
        };
    }

    private static long millisSince(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    @Override
    public String toString() {
        return "AnthropicLlmClient[model=" + properties.anthropicModel() + "]";
    }
}
