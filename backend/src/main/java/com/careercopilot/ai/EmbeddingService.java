package com.careercopilot.ai;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Keeps resume and job embeddings and compares them. Everything here is best effort: a missing key, an unavailable
 * pgvector store, a provider error or the daily limit all end in "no embedding", never in a failed request.
 * Only exception class names are logged, never texts or vectors.
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final AiAvailability availability;
    private final EmbeddingStore store;
    private final AiUsageLimiter limiter;
    private final AiProperties properties;

    public EmbeddingService(AiAvailability availability, EmbeddingStore store, AiUsageLimiter limiter,
            AiProperties properties) {
        this.availability = availability;
        this.store = store;
        this.limiter = limiter;
        this.properties = properties;
    }

    /** True when a key is set and the database can store vectors. */
    public boolean isActive() {
        return availability.isEmbeddingEnabled() && store.isAvailable();
    }

    /** Embeds a resume's text and stores it. Returns false when nothing was stored. Never throws. */
    public boolean embedResume(Long userId, Long resumeId, String text) {
        return embed(userId, text, "resume", vector -> store.saveResumeEmbedding(resumeId, vector));
    }

    /** Embeds a job (title and description) unless it already has an embedding. Returns true when one exists. */
    public boolean ensureJobEmbedding(Long userId, Long jobId, String text) {
        try {
            if (!isActive()) {
                return false;
            }
            if (store.jobEmbeddingExists(jobId)) {
                return true;
            }
        } catch (RuntimeException e) {
            log.warn("Could not check the job embedding: {}", e.getClass().getSimpleName());
            return false;
        }
        return embed(userId, text, "job", vector -> store.saveJobEmbedding(jobId, vector));
    }

    /** Same for a resume that was uploaded before embeddings were switched on. */
    public boolean ensureResumeEmbedding(Long userId, Long resumeId, String text) {
        try {
            if (!isActive()) {
                return false;
            }
            if (store.resumeEmbeddingExists(resumeId)) {
                return true;
            }
        } catch (RuntimeException e) {
            log.warn("Could not check the resume embedding: {}", e.getClass().getSimpleName());
            return false;
        }
        return embedResume(userId, resumeId, text);
    }

    /** Cosine similarity of the two stored embeddings, empty when either is missing. Never throws. */
    public Optional<Double> similarity(Long resumeId, Long jobId) {
        try {
            return isActive() ? store.cosineSimilarity(resumeId, jobId) : Optional.empty();
        } catch (RuntimeException e) {
            log.warn("Could not compare embeddings: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** Maps a cosine similarity linearly from [low, high] to 0..100 (text embeddings rarely go below 0.3). */
    public int semanticScore(double cosine) {
        double low = properties.semanticLow();
        double high = properties.semanticHigh();
        if (high <= low) {
            return cosine >= high ? 100 : 0;
        }
        double scaled = Math.max(0.0, Math.min(1.0, (cosine - low) / (high - low)));
        return (int) Math.round(100 * scaled);
    }

    private boolean embed(Long userId, String text, String kind, java.util.function.Consumer<float[]> save) {
        try {
            if (!isActive() || text == null || text.isBlank()) {
                return false;
            }
            limiter.checkAndRecord(userId);
            EmbeddingClient client = availability.embedding().orElseThrow();
            save.accept(client.embed(text, EmbeddingInputType.DOCUMENT));
            return true;
        } catch (RuntimeException e) {
            log.warn("Could not embed a {}: {}", kind, e.getClass().getSimpleName());
            return false;
        }
    }
}
