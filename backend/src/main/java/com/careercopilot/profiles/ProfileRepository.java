package com.careercopilot.profiles;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ProfileRepository extends JpaRepository<Profile, Long> {

    Optional<Profile> findByUserId(Long userId);

    /** Profiles that have an uploaded resume marked as base, i.e. the ones the draft agent can work for. */
    @Query("select p from Profile p where exists (select r.id from com.careercopilot.resumes.Resume r "
            + "where r.profileId = p.id and r.isBase = true and r.baseResumeId is null)")
    java.util.List<Profile> findAllWithBaseResume();

    /** Targeted update so a run never overwrites profile edits made at the same moment. */
    @Transactional
    @Modifying
    @Query("update Profile p set p.lastAgentRunAt = :at where p.id = :id")
    int updateLastAgentRunAt(@Param("id") Long id, @Param("at") Instant at);
}
