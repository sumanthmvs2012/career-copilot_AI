package com.careercopilot.resumes;

import java.util.List;

/** Builds the text of a tailored copy; the original text is always kept unchanged at the end. */
final class TailoredTextBuilder {

    private TailoredTextBuilder() {
    }

    /**
     * A headline line (job title, else profile headline), a "Relevant skills" line with the resume skills the
     * job asks for, a blank line, then the original text unchanged. Missing parts are left out.
     */
    static String build(String headline, List<String> relevantSkills, String originalText) {
        return build(headline, null, relevantSkills, originalText);
    }

    /** Same, with an optional "Summary:" line (written by Claude from facts in the resume) after the headline. */
    static String build(String headline, String summary, List<String> relevantSkills, String originalText) {
        StringBuilder text = new StringBuilder();
        if (headline != null && !headline.isBlank()) {
            text.append(headline.strip()).append('\n');
        }
        if (summary != null && !summary.isBlank()) {
            text.append("Summary: ").append(summary.strip()).append('\n');
        }
        if (relevantSkills != null && !relevantSkills.isEmpty()) {
            text.append("Relevant skills: ").append(String.join(", ", relevantSkills)).append('\n');
        }
        if (!text.isEmpty()) {
            text.append('\n');
        }
        return text.append(originalText == null ? "" : originalText).toString();
    }
}
