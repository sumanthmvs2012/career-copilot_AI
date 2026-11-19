package com.careercopilot.ai;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory EmbeddingStore for service tests. Cosine similarity is computed on the stored vectors. */
public class FakeEmbeddingStore implements EmbeddingStore {

    private final boolean available;
    public final Map<Long, float[]> resumes = new HashMap<>();
    public final Map<Long, float[]> jobs = new HashMap<>();
    public boolean failOnSave;

    public FakeEmbeddingStore(boolean available) {
        this.available = available;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public void saveResumeEmbedding(Long resumeId, float[] embedding) {
        if (failOnSave) {
            throw new IllegalStateException("db down");
        }
        resumes.put(resumeId, embedding);
    }

    @Override
    public void saveJobEmbedding(Long jobId, float[] embedding) {
        if (failOnSave) {
            throw new IllegalStateException("db down");
        }
        jobs.put(jobId, embedding);
    }

    @Override
    public boolean resumeEmbeddingExists(Long resumeId) {
        return resumes.containsKey(resumeId);
    }

    @Override
    public boolean jobEmbeddingExists(Long jobId) {
        return jobs.containsKey(jobId);
    }

    @Override
    public Optional<Double> cosineSimilarity(Long resumeId, Long jobId) {
        float[] a = resumes.get(resumeId);
        float[] b = jobs.get(jobId);
        if (a == null || b == null) {
            return Optional.empty();
        }
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return Optional.of(dot / (Math.sqrt(na) * Math.sqrt(nb)));
    }
}
