package com.careercopilot.resumes;

import java.time.Instant;
import java.util.List;

public record TailoredResumeResponse(
        Long id,
        String fileName,
        String contentType,
        boolean isBase,
        Long baseResumeId,
        Long jobId,
        String jobTitle,
        String company,
        int textLength,
        List<String> skills,
        Instant createdAt,
        String parsedText,
        List<SuggestionResponse> suggestions) {

    public static TailoredResumeResponse from(ResumeDetailResponse detail, List<SuggestionResponse> suggestions) {
        return new TailoredResumeResponse(detail.id(), detail.fileName(), detail.contentType(), detail.isBase(),
                detail.baseResumeId(), detail.jobId(), detail.jobTitle(), detail.company(), detail.textLength(),
                detail.skills(), detail.createdAt(), detail.parsedText(), suggestions);
    }
}
