package com.careercopilot.agent;

import java.util.List;

/**
 * Rule-based cover letter. It uses only the job title, the company, the candidate's own name and the skills
 * that are on the resume and asked for by the job, so it never claims anything the resume does not show.
 */
public final class CoverLetterTemplate {

    static final int MAX_SKILLS = 5;

    private CoverLetterTemplate() {
    }

    public static String build(String fullName, String jobTitle, String company, List<String> matchedSkills) {
        String where = company == null || company.isBlank() ? "your team" : company.strip();
        StringBuilder text = new StringBuilder("Dear Hiring Team,\n\n");
        text.append("I am writing to apply for the ").append(jobTitle.strip()).append(" role at ").append(where)
                .append('.');
        if (matchedSkills != null && !matchedSkills.isEmpty()) {
            text.append(" My resume lists experience with ")
                    .append(join(matchedSkills.subList(0, Math.min(MAX_SKILLS, matchedSkills.size()))))
                    .append(", which the posting asks for.");
        }
        text.append("\n\nMy resume is attached. I would be glad to talk about how my background fits the role.\n\n");
        text.append("Kind regards,\n").append(fullName == null ? "" : fullName.strip());
        return text.toString().stripTrailing();
    }

    private static String join(List<String> skills) {
        if (skills.size() == 1) {
            return skills.get(0);
        }
        String head = String.join(", ", skills.subList(0, skills.size() - 1));
        return head + " and " + skills.get(skills.size() - 1);
    }
}
