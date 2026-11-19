package com.careercopilot.ai;

/** A large language model. Only exists as a bean when ANTHROPIC_API_KEY is set. */
public interface LlmClient {

    /**
     * Asks for a reply that matches the JSON schema derived from {@code type} and returns it parsed. Throws
     * AiProviderException (fixed message) on any provider failure, a refusal or a cut-off reply.
     *
     * @param maxTokens 0 or less uses the configured default
     */
    <T> T completeStructured(String system, String user, Class<T> type, int maxTokens);
}
