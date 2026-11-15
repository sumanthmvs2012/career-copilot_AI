package com.careercopilot.jobs;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobLocationRepository extends JpaRepository<JobLocation, Long> {

    /** One query for a whole page of jobs, so listing never loads locations job by job. */
    List<JobLocation> findByJobIdIn(Collection<Long> jobIds);

    /** Flushes pending inserts first so a replace (delete then insert) inside one transaction stays ordered. */
    @Modifying(flushAutomatically = true)
    @Query("delete from JobLocation l where l.jobId in :jobIds")
    void deleteByJobIds(@Param("jobIds") Collection<Long> jobIds);
}
