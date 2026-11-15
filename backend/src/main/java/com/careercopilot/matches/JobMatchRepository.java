package com.careercopilot.matches;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobMatchRepository extends JpaRepository<JobMatch, Long> {

    Optional<JobMatch> findByProfileIdAndJobId(Long profileId, Long jobId);
}
