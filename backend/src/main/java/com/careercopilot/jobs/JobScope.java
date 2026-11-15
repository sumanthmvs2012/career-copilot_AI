package com.careercopilot.jobs;

import java.util.Locale;

public enum JobScope {
    PASTED, SCRAPED, ALL;

    /** Null or blank means both. */
    public static JobScope parse(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "pasted" -> PASTED;
            case "scraped" -> SCRAPED;
            default -> throw new InvalidJobQueryException("Scope must be pasted or scraped.");
        };
    }
}
