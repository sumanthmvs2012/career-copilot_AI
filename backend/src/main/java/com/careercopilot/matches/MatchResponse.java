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
        Instant createdAt) {

    public static MatchResponse from(JobMatch match) {
        return new MatchResponse(
                match.getJobId(),
                match.getResumeId(),
                match.getScore(),
                SkillJson.readStrings(match.getMatchedSkills()),
                SkillJson.readStrings(match.getMissingSkills()),
                SkillJson.readNearMatches(match.getNearMatchedSkills()),
                SkillJson.readStrings(match.getMissingKeywords()),
                match.getSuggestedHeadline(),
                new LocationFit(match.getLocationFitStatus(), match.getLocationFitNote()),
                // Re-running replaces the row, so this is the time the stored result was computed.
                match.getCreatedAt());
    }
}
