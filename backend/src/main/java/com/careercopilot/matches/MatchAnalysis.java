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
        LocationFit locationFit,
        String scoreSource) {

    public static final String SOURCE_KEYWORDS = "keywords";
    public static final String SOURCE_EMBEDDINGS = "keywords+embeddings";

    /** A keyword-only result. */
    public MatchAnalysis(int score, List<String> jobSkills, List<String> matchedSkills, List<String> missingSkills,
            List<NearMatch> nearMatchedSkills, List<String> missingKeywords, String suggestedHeadline,
            LocationFit locationFit) {
        this(score, jobSkills, matchedSkills, missingSkills, nearMatchedSkills, missingKeywords, suggestedHeadline,
                locationFit, SOURCE_KEYWORDS);
    }
}
