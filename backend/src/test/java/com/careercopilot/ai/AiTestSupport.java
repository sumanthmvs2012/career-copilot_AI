package com.careercopilot.ai;

import java.time.Duration;

/** Shared builders for the AI tests. The keys are obvious fakes so tests can check they never leak. */
public final class AiTestSupport {

    public static final String LLM_KEY = "sk-ant-test-secret-key";
    public static final String VOYAGE_KEY = "pa-test-secret-key";

    private AiTestSupport() {
    }

    public static AiProperties properties() {
        return properties(20000);
    }

    public static AiProperties properties(int maxInputChars) {
        return new AiProperties(LLM_KEY, "claude-opus-5-5", 16000, "medium", true, Duration.ofSeconds(5), VOYAGE_KEY,
                "voyage-3.5-lite", 50, maxInputChars, Duration.ofSeconds(5), 0.3, 0.8);
    }

    /** Claude switched off (no key): every feature uses its rule-based result. */
    public static LlmService disabledLlm() {
        return new LlmService(AiAvailability.disabled(), new AiUsageLimiter(properties()));
    }

    /** Claude switched on, answered by the given client. */
    public static LlmService llm(LlmClient client) {
        return new LlmService(new AiAvailability(client, null), new AiUsageLimiter(properties()));
    }

    /** A transaction manager that does nothing, for unit tests of services that use TransactionTemplate. */
    public static org.springframework.transaction.PlatformTransactionManager noTransactions() {
        return org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);
    }

    /** A 1024-dimension vector whose first value is the given one and the rest are zero. */
    public static float[] vector(float first) {
        float[] values = new float[EmbeddingClient.DIMENSIONS];
        values[0] = first;
        return values;
    }

    /** A 1024-dimension vector with two non-zero values, to get a chosen angle against vector(1). */
    public static float[] vector(float first, float second) {
        float[] values = vector(first);
        values[1] = second;
        return values;
    }
}
