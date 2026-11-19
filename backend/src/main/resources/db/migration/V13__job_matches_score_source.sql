-- How a stored match score was computed: 'keywords' or 'keywords+embeddings'. Null (older rows) means keywords.
ALTER TABLE job_matches ADD COLUMN score_source VARCHAR(30);
