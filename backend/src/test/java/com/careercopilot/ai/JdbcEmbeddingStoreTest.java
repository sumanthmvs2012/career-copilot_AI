package com.careercopilot.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs every migration (including the PostgreSQL-only db/migration-pg) on real PostgreSQL + pgvector, then checks
 * the native vector SQL. Skipped when Docker is not running; the H2 tests cover everything else.
 */
@Testcontainers(disabledWithoutDocker = true)
class JdbcEmbeddingStoreTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    private static JdbcTemplate jdbc;

    private JdbcEmbeddingStore store;
    private long resumeId;
    private long jobId;

    @BeforeAll
    static void migrate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/migration-pg")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void rows() {
        jdbc.update("DELETE FROM resumes");
        jdbc.update("DELETE FROM jobs");
        jdbc.update("DELETE FROM profiles");
        jdbc.update("DELETE FROM users");
        long userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash) VALUES ('a@example.com', 'x') RETURNING id", Long.class);
        long profileId = jdbc.queryForObject(
                "INSERT INTO profiles (user_id, full_name) VALUES (?, 'A') RETURNING id", Long.class, userId);
        resumeId = jdbc.queryForObject("""
                INSERT INTO resumes (profile_id, file_path, file_name, content_type)
                VALUES (?, 'k', 'cv.pdf', 'application/pdf') RETURNING id
                """, Long.class, profileId);
        jobId = jdbc.queryForObject(
                "INSERT INTO jobs (source, title, description) VALUES ('manual', 'Dev', 'Java') RETURNING id",
                Long.class);
        store = new JdbcEmbeddingStore(jdbc);
    }

    @Test
    void detectsPgvectorColumns() {
        assertThat(store.isAvailable()).isTrue();
    }

    @Test
    void storesEmbeddingsAndReportsThemAsPresent() {
        assertThat(store.resumeEmbeddingExists(resumeId)).isFalse();
        assertThat(store.jobEmbeddingExists(jobId)).isFalse();

        store.saveResumeEmbedding(resumeId, AiTestSupport.vector(1f));
        store.saveJobEmbedding(jobId, AiTestSupport.vector(1f));

        assertThat(store.resumeEmbeddingExists(resumeId)).isTrue();
        assertThat(store.jobEmbeddingExists(jobId)).isTrue();
    }

    @Test
    void cosineSimilarityMatchesTheAngleBetweenVectors() {
        store.saveResumeEmbedding(resumeId, AiTestSupport.vector(1f));
        // 45 degrees from the resume vector: cosine = 1/sqrt(2).
        store.saveJobEmbedding(jobId, AiTestSupport.vector(1f, 1f));

        assertThat(store.cosineSimilarity(resumeId, jobId)).hasValueSatisfying(
                cosine -> assertThat(cosine).isCloseTo(1 / Math.sqrt(2), within(1e-4)));
    }

    @Test
    void cosineSimilarityIsEmptyUntilBothEmbeddingsExist() {
        store.saveResumeEmbedding(resumeId, AiTestSupport.vector(1f));

        assertThat(store.cosineSimilarity(resumeId, jobId)).isEmpty();
    }
}
