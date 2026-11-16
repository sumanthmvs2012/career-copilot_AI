package com.careercopilot.matches;

import java.time.Instant;
import java.util.List;

public record MatchResponse(
        Long jobId,
        Long resumeId,
        int score,
        List<String> matchedSkills,
        List<String> missingSkills,
        List<NearMatch> nearMatchedSkills,
        List<String> missingKeywords,
        String suggestedHeadline,
        LocationFit locationFit,
        Instant createdAt,
        String note) {

    static final String NO_SKILLS_NOTE =
            "No known skills were found in this job description, so the score cannot be calculated.";

    public static MatchResponse from(JobMatch match) {
        List<String> matched = SkillJson.readStrings(match.getMatchedSkills());
        List<String> missing = SkillJson.readStrings(match.getMissingSkills());
        return new MatchResponse(
                match.getJobId(),
                match.getResumeId(),
                match.getScore(),
                matched,
                missing,
                SkillJson.readNearMatches(match.getNearMatchedSkills()),
                SkillJson.readStrings(match.getMissingKeywords()),
                match.getSuggestedHeadline(),
                new LocationFit(match.getLocationFitStatus(), match.getLocationFitNote()),
                // Re-running replaces the row, so this is the time the stored result was computed.
                match.getCreatedAt(),
                matched.isEmpty() && missing.isEmpty() ? NO_SKILLS_NOTE : null);
    }
}
