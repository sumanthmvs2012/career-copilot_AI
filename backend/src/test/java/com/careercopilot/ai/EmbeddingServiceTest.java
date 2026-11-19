package com.careercopilot.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmbeddingServiceTest {

    /** Records the calls and returns a fixed vector, or fails. */
    private static final class FakeClient implements EmbeddingClient {
        final List<EmbeddingInputType> calls = new ArrayList<>();
        boolean fail;

        @Override
        public float[] embed(String text, EmbeddingInputType inputType) {
            calls.add(inputType);
            if (fail) {
                throw new AiProviderException("The AI provider request failed.", 500);
            }
            return AiTestSupport.vector(1f);
        }
    }

    private final FakeClient client = new FakeClient();
    private final FakeEmbeddingStore store = new FakeEmbeddingStore(true);

    private EmbeddingService service(int limit) {
        return new EmbeddingService(new AiAvailability((LlmClient) null, client), store,
                new AiUsageLimiter(limit, Clock.systemUTC()), AiTestSupport.properties());
    }

    @Test
    void embedsAResumeAsADocumentAndStoresIt() {
        assertThat(service(5).embedResume(1L, 10L, "resume text")).isTrue();

        assertThat(store.resumes).containsKey(10L);
        assertThat(client.calls).containsExactly(EmbeddingInputType.DOCUMENT);
    }

    @Test
    void doesNothingWithoutAKey() {
        EmbeddingService off = new EmbeddingService(AiAvailability.disabled(), store,
                new AiUsageLimiter(5, Clock.systemUTC()), AiTestSupport.properties());

        assertThat(off.isActive()).isFalse();
        assertThat(off.embedResume(1L, 10L, "text")).isFalse();
        assertThat(off.ensureJobEmbedding(1L, 20L, "text")).isFalse();
        assertThat(off.ensureResumeEmbedding(1L, 10L, "text")).isFalse();
        assertThat(off.similarity(10L, 20L)).isEmpty();
        assertThat(store.resumes).isEmpty();
    }

    @Test
    void doesNothingWhenTheStoreIsUnavailable() {
        EmbeddingService noStore = new EmbeddingService(new AiAvailability((LlmClient) null, client),
                new FakeEmbeddingStore(false), new AiUsageLimiter(5, Clock.systemUTC()), AiTestSupport.properties());

        assertThat(noStore.isActive()).isFalse();
        assertThat(noStore.embedResume(1L, 10L, "text")).isFalse();
        assertThat(client.calls).isEmpty();
    }

    @Test
    void aProviderFailureIsSwallowed() {
        client.fail = true;

        assertThat(service(5).embedResume(1L, 10L, "text")).isFalse();
        assertThat(store.resumes).isEmpty();
    }

    @Test
    void aStoreFailureIsSwallowed() {
        store.failOnSave = true;

        assertThat(service(5).embedResume(1L, 10L, "text")).isFalse();
    }

    @Test
    void blankTextIsNotSent() {
        EmbeddingService service = service(5);

        assertThat(service.embedResume(1L, 10L, "  ")).isFalse();
        assertThat(service.embedResume(1L, 10L, null)).isFalse();
        assertThat(client.calls).isEmpty();
    }

    @Test
    void theDailyLimitStopsEmbeddingWithoutAnError() {
        EmbeddingService limited = service(1);

        assertThat(limited.embedResume(1L, 10L, "a")).isTrue();
        assertThat(limited.embedResume(1L, 11L, "b")).isFalse();
        assertThat(client.calls).hasSize(1);
    }

    @Test
    void aJobIsEmbeddedOnlyOnce() {
        EmbeddingService service = service(5);

        assertThat(service.ensureJobEmbedding(1L, 20L, "job")).isTrue();
        assertThat(service.ensureJobEmbedding(1L, 20L, "job")).isTrue();

        assertThat(client.calls).hasSize(1);
    }

    @Test
    void anExistingResumeEmbeddingIsReused() {
        store.resumes.put(10L, AiTestSupport.vector(1f));

        assertThat(service(5).ensureResumeEmbedding(1L, 10L, "text")).isTrue();
        assertThat(client.calls).isEmpty();
    }

    @Test
    void similarityComparesTheStoredVectors() {
        EmbeddingService service = service(5);
        service.embedResume(1L, 10L, "a");
        service.ensureJobEmbedding(1L, 20L, "b");

        assertThat(service.similarity(10L, 20L)).hasValueSatisfying(value -> assertThat(value).isCloseTo(1.0, within(1e-9)));
        assertThat(service.similarity(10L, 99L)).isEmpty();
    }

    @Test
    void semanticScoreMapsTheConfiguredRangeLinearly() {
        EmbeddingService service = service(5);

        assertThat(service.semanticScore(0.3)).isZero();
        assertThat(service.semanticScore(0.05)).isZero();
        assertThat(service.semanticScore(0.55)).isEqualTo(50);
        assertThat(service.semanticScore(0.8)).isEqualTo(100);
        assertThat(service.semanticScore(0.95)).isEqualTo(100);
        assertThat(service.semanticScore(0.7)).isEqualTo(80);
    }

    @Test
    void aDegenerateRangeDoesNotDivideByZero() {
        AiProperties flat = new AiProperties("", "m", 1, "low", false, Duration.ofSeconds(1), "k", "v", 5, 100,
                Duration.ofSeconds(1), 0.5, 0.5);
        EmbeddingService service = new EmbeddingService(AiAvailability.disabled(), store,
                new AiUsageLimiter(5, Clock.systemUTC()), flat);

        assertThat(service.semanticScore(0.6)).isEqualTo(100);
        assertThat(service.semanticScore(0.4)).isZero();
    }

    @Test
    void availabilityReportsWhatIsConfigured() {
        assertThat(AiAvailability.disabled().isLlmEnabled()).isFalse();
        assertThat(AiAvailability.disabled().isEmbeddingEnabled()).isFalse();
        assertThat(AiAvailability.disabled().llm()).isEmpty();
        AiAvailability on = new AiAvailability((LlmClient) null, client);
        assertThat(on.isEmbeddingEnabled()).isTrue();
        assertThat(on.embedding()).containsSame(client);
    }
}
