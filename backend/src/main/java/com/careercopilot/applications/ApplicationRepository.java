package com.careercopilot.applications;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findByProfileIdOrderByCreatedAtDescIdDesc(Long profileId);

    Optional<Application> findByIdAndProfileId(Long id, Long profileId);

    boolean existsByProfileIdAndJobId(Long profileId, Long jobId);

    /** Applications that were sent, so the mailbox scan knows from when to look for replies. */
    List<Application> findByProfileIdAndSubmittedAtIsNotNull(Long profileId);

    /** Applied applications at or before the cutoff that have neither an employer reply nor a reminder yet. */
    @Query("select a from Application a where a.status = 'applied' and a.submittedAt <= :cutoff "
            + "and not exists (select l.id from com.careercopilot.mail.EmailLog l where l.applicationId = a.id "
            + "and l.type in ('employer_reply', 'reminder'))")
    List<Application> findDueForReminder(@Param("cutoff") Instant cutoff);
}
