package com.careercopilot.ai;

import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Says which AI features are configured. Everything AI-related falls back to rule-based when this says no. */
@Service
public class AiAvailability {

    private final LlmClient llm;
    private final EmbeddingClient embedding;

    @Autowired
    public AiAvailability(ObjectProvider<LlmClient> llm, ObjectProvider<EmbeddingClient> embedding) {
        this(llm.getIfAvailable(), embedding.getIfAvailable());
    }

    public AiAvailability(LlmClient llm, EmbeddingClient embedding) {
        this.llm = llm;
        this.embedding = embedding;
    }

    public static AiAvailability disabled() {
        return new AiAvailability((LlmClient) null, (EmbeddingClient) null);
    }

    public boolean isLlmEnabled() {
        return llm != null;
    }

    public boolean isEmbeddingEnabled() {
        return embedding != null;
    }

    public Optional<LlmClient> llm() {
        return Optional.ofNullable(llm);
    }

    public Optional<EmbeddingClient> embedding() {
        return Optional.ofNullable(embedding);
    }
}
