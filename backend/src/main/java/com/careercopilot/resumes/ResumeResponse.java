package com.careercopilot.resumes;

import com.careercopilot.matches.SkillJson;
import java.time.Instant;
import java.util.List;

public record ResumeResponse(
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
        List<String> skills) {

    /** For an uploaded resume: no job. */
    public static ResumeResponse from(Resume resume) {
        return from(resume, null);
    }

    public static ResumeResponse from(Resume resume, ResumeJobLabel label) {
        return new ResumeResponse(
                resume.getId(),
                resume.getFileName(),
                resume.getContentType(),
                resume.isBase(),
                resume.getBaseResumeId(),
                resume.getJobId(),
                label == null ? null : label.jobTitle(),
                label == null ? null : label.company(),
                resume.getParsedText() == null ? 0 : resume.getParsedText().length(),
                resume.getCreatedAt(),
                SkillJson.readStrings(resume.getSkills()));
    }
}
