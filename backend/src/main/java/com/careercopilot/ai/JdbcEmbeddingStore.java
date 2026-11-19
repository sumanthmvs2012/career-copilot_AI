package com.careercopilot.ai;

import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** pgvector access with native SQL: '[..]'::vector literals and the cosine distance operator {@code <=>}. */
@Repository
public class JdbcEmbeddingStore implements EmbeddingStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcEmbeddingStore.class);

    private final JdbcTemplate jdbc;
    private volatile Boolean available;

    public JdbcEmbeddingStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Decided on first use (after Flyway has run) and then fixed for the life of the process. */
    @Override
    public boolean isAvailable() {
        Boolean known = available;
        if (known == null) {
            synchronized (this) {
                if (available == null) {
                    available = detect();
                    log.info("pgvector embeddings available: {}", available);
                }
                known = available;
            }
        }
        return known;
    }

    private boolean detect() {
        try {
            String product = jdbc.execute((java.sql.Connection connection) -> {
                DatabaseMetaData meta = connection.getMetaData();
                return meta.getDatabaseProductName();
            });
            if (product == null || !product.toLowerCase(java.util.Locale.ROOT).contains("postgres")) {
                return false;
            }
            Integer columns = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = current_schema() AND column_name = 'embedding'
                      AND udt_name = 'vector' AND table_name IN ('resumes', 'jobs')
                    """, Integer.class);
            return columns != null && columns == 2;
        } catch (RuntimeException e) {
            log.warn("Could not check for pgvector: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public void saveResumeEmbedding(Long resumeId, float[] embedding) {
        if (isAvailable()) {
            jdbc.update("UPDATE resumes SET embedding = ?::vector WHERE id = ?", literal(embedding), resumeId);
        }
    }

    @Override
    public void saveJobEmbedding(Long jobId, float[] embedding) {
        if (isAvailable()) {
            jdbc.update("UPDATE jobs SET embedding = ?::vector WHERE id = ?", literal(embedding), jobId);
        }
    }

    @Override
    public boolean resumeEmbeddingExists(Long resumeId) {
        return exists("SELECT COUNT(*) FROM resumes WHERE id = ? AND embedding IS NOT NULL", resumeId);
    }

    @Override
    public boolean jobEmbeddingExists(Long jobId) {
        return exists("SELECT COUNT(*) FROM jobs WHERE id = ? AND embedding IS NOT NULL", jobId);
    }

    private boolean exists(String sql, Long id) {
        if (!isAvailable()) {
            return false;
        }
        Integer count = jdbc.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }

    @Override
    public Optional<Double> cosineSimilarity(Long resumeId, Long jobId) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        List<Double> found = jdbc.queryForList("""
                SELECT 1 - (r.embedding <=> j.embedding)
                FROM resumes r, jobs j
                WHERE r.id = ? AND j.id = ? AND r.embedding IS NOT NULL AND j.embedding IS NOT NULL
                """, Double.class, resumeId, jobId);
        return found.isEmpty() || found.get(0) == null ? Optional.empty() : Optional.of(found.get(0));
    }

    /** pgvector text form, for example [0.1,0.2]. Rejects NaN and infinity, which the database would refuse anyway. */
    static String literal(float[] embedding) {
        StringBuilder text = new StringBuilder(embedding.length * 10).append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (!Float.isFinite(embedding[i])) {
                throw new IllegalArgumentException("Embedding contains a non-finite value.");
            }
            if (i > 0) {
                text.append(',');
            }
            text.append(embedding[i]);
        }
        return text.append(']').toString();
    }
}
