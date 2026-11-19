package com.careercopilot.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** AI settings, all optional. A provider is "configured" when its key is present. Keys come from the environment. */
@ConfigurationProperties("app.ai")
public record AiProperties(
        @DefaultValue("") String anthropicApiKey,
        @DefaultValue("claude-sonnet-5-5") String anthropicModel,
        @DefaultValue("1024") int anthropicMaxTokens,
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
                + ", dailyLimitPerUser=" + dailyLimitPerUser + ", maxInputChars=" + maxInputChars + "]";
    }
}
