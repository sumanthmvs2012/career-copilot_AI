package com.careercopilot.resumes;

import java.time.Instant;
import java.util.List;

public record ResumeDetailResponse(
        Long id,
        String fileName,
        String contentType,
        boolean isBase,
        Long baseResumeId,
        Long jobId,
        String jobTitle,
        String company,
        int textLength,
        Instant createdAt,
        List<String> skills,
        String parsedText) {

    public static ResumeDetailResponse from(Resume resume) {
        return from(resume, null);
    }

    public static ResumeDetailResponse from(Resume resume, ResumeJobLabel label) {
        ResumeResponse base = ResumeResponse.from(resume, label);
        return new ResumeDetailResponse(base.id(), base.fileName(), base.contentType(), base.isBase(),
                base.baseResumeId(), base.jobId(), base.jobTitle(), base.company(), base.textLength(),
                base.createdAt(), base.skills(), resume.getParsedText());
    }
}
