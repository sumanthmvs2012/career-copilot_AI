package com.careercopilot.matches;

import java.util.List;
import java.util.Locale;

/** A company's own name is not a skill the candidate needs ("Stripe" in a Stripe job post). */
public final class CompanySkills {

    private CompanySkills() {
    }

    /** The skills without any whose name equals the company name (case and punctuation ignored). */
    public static List<String> exclude(List<String> skills, String companyName) {
        String company = fold(companyName);
        if (company.isEmpty()) {
            return skills;
        }
        return skills.stream().filter(skill -> !fold(skill).equals(company)).toList();
    }

    private static String fold(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
