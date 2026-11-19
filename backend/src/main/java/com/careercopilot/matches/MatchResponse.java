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
        String note,
        String scoreSource) {

    static final String NO_SKILLS_NOTE =
            "No known skills were found in this job description, so the score cannot be calculated.";

    static final String SEMANTIC_ONLY_NOTE = "Score is based on text similarity only.";

    public static MatchResponse from(JobMatch match) {
        List<String> matched = SkillJson.readStrings(match.getMatchedSkills());
        List<String> missing = SkillJson.readStrings(match.getMissingSkills());
        String source = match.getScoreSource() == null ? MatchAnalysis.SOURCE_KEYWORDS : match.getScoreSource();
        boolean noSkills = matched.isEmpty() && missing.isEmpty();
        String note = !noSkills ? null
                : MatchAnalysis.SOURCE_EMBEDDINGS.equals(source) ? SEMANTIC_ONLY_NOTE : NO_SKILLS_NOTE;
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
                note,
                source);
    }
}
