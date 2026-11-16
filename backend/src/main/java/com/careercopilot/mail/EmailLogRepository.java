package com.careercopilot.mail;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {

    List<EmailLog> findByApplicationIdOrderByOccurredAtDescIdDesc(Long applicationId);

    /** One query for many applications, so the application list has no N+1. */
    List<EmailLog> findByApplicationIdInAndTypeOrderByOccurredAtDescIdDesc(Collection<Long> applicationIds,
            String type);

    boolean existsByApplicationIdAndExternalMessageId(Long applicationId, String externalMessageId);

    @Query("select l from EmailLog l where l.type = 'reminder' and l.status = 'new' and l.applicationId in "
            + "(select a.id from com.careercopilot.applications.Application a where a.profileId = :profileId) "
            + "order by l.occurredAt desc, l.id desc")
    List<EmailLog> findNewRemindersOfProfile(@Param("profileId") Long profileId);

    @Query("select l from EmailLog l where l.id = :id and l.type = 'reminder' and l.applicationId in "
            + "(select a.id from com.careercopilot.applications.Application a where a.profileId = :profileId)")
    Optional<EmailLog> findReminderOfProfile(@Param("id") Long id, @Param("profileId") Long profileId);
}
