package com.careercopilot.mail;

import java.time.Instant;

/** Subject, time and classification only; a body or snippet is never stored, so it cannot be returned. */
public record EmailLogResponse(Long id, String type, String classification, String subject, Instant occurredAt,
        String status) {

    static EmailLogResponse from(EmailLog log) {
        return new EmailLogResponse(log.getId(), log.getType(), log.getClassification(), log.getSubject(),
                log.getOccurredAt(), log.getStatus());
    }
}
