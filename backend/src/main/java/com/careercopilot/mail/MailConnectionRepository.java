package com.careercopilot.mail;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailConnectionRepository extends JpaRepository<MailConnection, Long> {

    Optional<MailConnection> findFirstByUserIdAndRevokedAtIsNull(Long userId);

    Optional<MailConnection> findByIdAndUserId(Long id, Long userId);

    /** Every connection the hourly scan should visit. */
    List<MailConnection> findByRevokedAtIsNull();
}
