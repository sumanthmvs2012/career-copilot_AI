package com.careercopilot.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** AI settings, all optional. A provider is "configured" when its key is present. Keys come from the environment. */
@ConfigurationProperties("app.ai")
public record AiProperties(
        @DefaultValue("") String anthropicApiKey,
        @DefaultValue("claude-opus-5-5") String anthropicModel,
        // Thinking tokens count toward max_tokens, so this leaves room for the reasoning plus the JSON answer.
        @DefaultValue("16000") int anthropicMaxTokens,
        // low, medium, high, xhigh or max. Claude Opus 5.5 defaults to medium; it is set explicitly on every call.
        @DefaultValue("medium") String anthropicEffort,
        // Lets the API retry a refused request on another model (beta server-side fallbacks).
        @DefaultValue("true") boolean anthropicFallbacks,
        // Claude replies with reasoning can take far longer than an embedding call.
        @DefaultValue("PT120S") Duration anthropicTimeout,
        @DefaultValue("") String voyageApiKey,
        @DefaultValue("voyage-3.5-lite") String voyageModel,
        @DefaultValue("50") int dailyLimitPerUser,
        @DefaultValue("20000") int maxInputChars,
        @DefaultValue("PT30S") Duration timeout,
        @DefaultValue("0.3") double semanticLow,
        @DefaultValue("0.8") double semanticHigh) {

    public boolean llmConfigured() {
        return anthropicApiKey != null && !anthropicApiKey.isBlank();
    }

    public boolean embeddingConfigured() {
        return voyageApiKey != null && !voyageApiKey.isBlank();
    }

    /** Never prints the keys, in case a properties object ends up in a log line. */
    @Override
    public String toString() {
        return "AiProperties[llmConfigured=" + llmConfigured() + ", embeddingConfigured=" + embeddingConfigured()
                + ", model=" + anthropicModel + ", dailyLimitPerUser=" + dailyLimitPerUser
                + ", maxInputChars=" + maxInputChars + "]";
    }
}
