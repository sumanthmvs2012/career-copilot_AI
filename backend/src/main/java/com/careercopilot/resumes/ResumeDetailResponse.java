package com.careercopilot.resumes;

import java.time.Instant;

public record ResumeDetailResponse(
        Long id,
        String fileName,
        String contentType,
        boolean isBase,
        Long baseResumeId,
        int textLength,
        Instant createdAt,
        String parsedText) {

    public static ResumeDetailResponse from(Resume resume) {
        ResumeResponse base = ResumeResponse.from(resume);
        return new ResumeDetailResponse(base.id(), base.fileName(), base.contentType(), base.isBase(),
                base.baseResumeId(), base.textLength(), base.createdAt(), resume.getParsedText());
    }
}
