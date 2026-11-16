package com.careercopilot.applications;

import com.careercopilot.mail.EmailLog;
import java.time.Instant;

/** The email fields come from the latest employer reply in email_logs (no reply: false, null, null). */
public record ApplicationResponse(
        Long id,
        Long jobId,
        String jobTitle,
        String company,
        Long resumeId,
        String status,
        String coverLetter,
        Instant appliedAt,
        String submittedBy,
        boolean emailSent,
        Instant emailSentAt,
        String emailClassification,
        Instant createdAt,
        Instant updatedAt) {

    public static ApplicationResponse from(Application a, String jobTitle, String company, EmailLog latestReply) {
        return new ApplicationResponse(a.getId(), a.getJobId(), jobTitle, company, a.getResumeId(), a.getStatus(),
                a.getCoverLetter(), a.getSubmittedAt(), a.getSubmittedBy(), latestReply != null,
                latestReply == null ? null : latestReply.getOccurredAt(),
                latestReply == null ? null : latestReply.getClassification(), a.getCreatedAt(), a.getUpdatedAt());
    }
}
