package com.careercopilot.jobs;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, Long> {

    /** Scraped (ownerless) jobs stored after the given moment, oldest first, for the draft agent. */
    List<Job> findByCreatedByProfileIdIsNullAndCreatedAtAfterOrderByCreatedAtAscIdAsc(Instant after, Pageable page);

    List<Job> findBySourceAndExternalIdIn(String source, Collection<String> externalIds);

    /** Keyset paging over jobs that have free-text location, for re-deriving their structured locations. */
    List<Job> findByLocationIsNotNullAndIdGreaterThanOrderByIdAsc(Long afterId, Pageable page);
}
