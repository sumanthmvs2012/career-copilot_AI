package com.careercopilot.resumes;

import java.util.List;

/** Builds the text of a tailored copy from the original text only; it never adds a skill or claim of its own. */
final class TailoredTextBuilder {

    private TailoredTextBuilder() {
    }

    /**
     * A headline line (job title, else profile headline), a "Relevant skills" line with the resume skills the
     * job asks for, a blank line, then the original text unchanged. Missing parts are left out.
     */
    static String build(String headline, List<String> relevantSkills, String originalText) {
        StringBuilder text = new StringBuilder();
        if (headline != null && !headline.isBlank()) {
            text.append(headline.strip()).append('\n');
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
