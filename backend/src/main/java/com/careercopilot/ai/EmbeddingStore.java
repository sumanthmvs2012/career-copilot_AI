package com.careercopilot.ai;

import java.util.Optional;

/**
 * Reads and writes the pgvector columns resumes.embedding and jobs.embedding (not mapped in JPA). On a database
 * without pgvector (H2 in tests) isAvailable() is false and every method does nothing and returns empty/false.
 */
public interface EmbeddingStore {

    /** True when the database is PostgreSQL and both embedding columns exist (checked once). */
    boolean isAvailable();

    void saveResumeEmbedding(Long resumeId, float[] embedding);

    void saveJobEmbedding(Long jobId, float[] embedding);

    boolean resumeEmbeddingExists(Long resumeId);

    boolean jobEmbeddingExists(Long jobId);

    /** 1 minus the cosine distance, or empty when either embedding is missing or the store is unavailable. */
    Optional<Double> cosineSimilarity(Long resumeId, Long jobId);
}
