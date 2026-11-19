package com.careercopilot.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The provider beans exist only when their key is set, so without keys nothing can call out. */
@Configuration
public class AiConfig {

    @Bean
    @ConditionalOnExpression("!'${app.ai.anthropic-api-key:}'.isBlank()")
    LlmClient llmClient(AiProperties properties) {
        return new AnthropicLlmClient(properties);
    }

    @Bean
    @ConditionalOnExpression("!'${app.ai.voyage-api-key:}'.isBlank()")
    EmbeddingClient embeddingClient(AiProperties properties) {
        return new VoyageEmbeddingClient(properties);
    }
}
