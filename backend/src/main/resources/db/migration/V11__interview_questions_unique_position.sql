-- Two simultaneous first requests must not store the same list twice: one question per position per profile and job.
CREATE UNIQUE INDEX uq_interview_questions_profile_job_position ON interview_questions (profile_id, job_id, position);
