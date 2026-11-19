package com.careercopilot.ai;

/** A large language model. Only exists as a bean when ANTHROPIC_API_KEY is set. */
public interface LlmClient {

    /** Returns the model's text reply. Throws AiProviderException (fixed message) on any provider failure. */
    String complete(String system, String user, int maxTokens);
}
