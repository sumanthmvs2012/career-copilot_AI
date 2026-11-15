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
        int textLength,
        Instant createdAt,
        List<String> skills) {

    public static ResumeResponse from(Resume resume) {
        return new ResumeResponse(
                resume.getId(),
                resume.getFileName(),
                resume.getContentType(),
                resume.isBase(),
                resume.getBaseResumeId(),
                resume.getParsedText() == null ? 0 : resume.getParsedText().length(),
                resume.getCreatedAt(),
                SkillJson.readStrings(resume.getSkills()));
    }
}
