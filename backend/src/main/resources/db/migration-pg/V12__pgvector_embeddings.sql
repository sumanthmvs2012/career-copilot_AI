-- PostgreSQL only (location classpath:db/migration-pg; H2 tests never run it).
-- Embeddings come from Voyage voyage-3.5-lite at 1024 dimensions. The columns are not mapped in JPA;
-- EmbeddingStore reads and writes them with native SQL. Null = not embedded yet.
CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE resumes ADD COLUMN embedding vector(1024);
ALTER TABLE jobs ADD COLUMN embedding vector(1024);

CREATE INDEX idx_jobs_embedding ON jobs USING hnsw (embedding vector_cosine_ops);
