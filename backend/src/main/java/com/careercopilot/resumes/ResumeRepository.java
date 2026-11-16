package com.careercopilot.resumes;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeRepository extends JpaRepository<Resume, Long> {

    Optional<Resume> findByProfileIdAndIsBaseTrue(Long profileId);

    List<Resume> findByProfileIdOrderByIsBaseDescCreatedAtDesc(Long profileId);

    /** Counts uploaded resumes only; tailored copies have a base_resume_id and are not counted. */
    long countByProfileIdAndBaseResumeIdIsNull(Long profileId);

    Optional<Resume> findFirstByProfileIdAndBaseResumeIdIsNullOrderByCreatedAtDesc(Long profileId);

    /** Tailored copies of one uploaded resume. */
    List<Resume> findByProfileIdAndBaseResumeId(Long profileId, Long baseResumeId);

    List<Resume> findByProfileIdAndBaseResumeIdAndJobId(Long profileId, Long baseResumeId, Long jobId);

    Optional<Resume> findByIdAndProfileId(Long id, Long profileId);
}
