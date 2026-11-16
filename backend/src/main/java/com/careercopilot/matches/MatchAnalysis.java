package com.careercopilot.matches;

import java.util.List;

/** The result of matching one resume against one job, before anything is stored. */
public record MatchAnalysis(
        int score,
        List<String> jobSkills,
        List<String> matchedSkills,
        List<String> missingSkills,
        List<NearMatch> nearMatchedSkills,
        List<String> missingKeywords,
        String suggestedHeadline,
        LocationFit locationFit) {
}
