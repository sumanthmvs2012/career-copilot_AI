package com.careercopilot.applications;

import java.time.Instant;

/** emailSent and emailSentAt stay false/null until email tracking ships. */
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
        Instant createdAt,
        Instant updatedAt) {

    public static ApplicationResponse from(Application a, String jobTitle, String company) {
        return new ApplicationResponse(a.getId(), a.getJobId(), jobTitle, company, a.getResumeId(), a.getStatus(),
                a.getCoverLetter(), a.getSubmittedAt(), a.getSubmittedBy(), false, null, a.getCreatedAt(),
                a.getUpdatedAt());
    }
}
