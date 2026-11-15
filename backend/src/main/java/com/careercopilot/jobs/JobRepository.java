package com.careercopilot.jobs;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findBySourceAndExternalIdIn(String source, Collection<String> externalIds);
}
