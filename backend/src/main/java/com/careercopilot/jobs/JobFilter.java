package com.careercopilot.jobs;

import com.careercopilot.jobs.scraper.CategoryClassifier;
import java.util.Locale;
import java.util.Set;

/** The validated optional filters of GET /jobs. Null means "no filter". */
public record JobFilter(String q, String source, String remoteType, String location, Integer postedWithin,
        int offset, String category, String country, String state, String city) {

    static final int MAX_TEXT = 100;
    static final int MAX_PLACE = 120;
    static final Set<String> SOURCES =
            Set.of("greenhouse", "lever", "ashby", "workable", "smartrecruiters", "manual");
    static final Set<String> REMOTE_TYPES = Set.of("remote", "hybrid", "onsite");
    static final Set<Integer> POSTED_WITHIN_DAYS = Set.of(1, 7, 30);

    public static final JobFilter NONE = new JobFilter(null, null, null, null, null, 0);

    /** A filter without category or place parts. */
    public JobFilter(String q, String source, String remoteType, String location, Integer postedWithin, int offset) {
        this(q, source, remoteType, location, postedWithin, offset, null, null, null, null);
    }

    /** Parses the raw query values; a value that is present but invalid throws InvalidJobQueryException. */
    public static JobFilter parse(String q, String source, String remoteType, String location,
            String postedWithin, String offset) {
        return parse(q, source, remoteType, location, postedWithin, offset, null, null, null, null);
    }

    public static JobFilter parse(String q, String source, String remoteType, String location,
            String postedWithin, String offset, String category, String country, String state, String city) {
        return new JobFilter(
                text(q, "q"),
                choice(source, SOURCES, "Source must be one of: greenhouse, lever, ashby, workable, smartrecruiters, manual."),
                choice(remoteType, REMOTE_TYPES, "Remote type must be remote, hybrid or onsite."),
                text(location, "location"),
                postedWithin(postedWithin),
                offset(offset),
                category(category),
                place(country, "country"),
                place(state, "state"),
                place(city, "city"));
    }

    /** Returns the canonical spelling so the stored value can be compared directly. */
    private static String category(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return CategoryClassifier.CATEGORIES.stream()
                .filter(known -> known.equalsIgnoreCase(trimmed))
                .findFirst()
                .orElseThrow(() -> new InvalidJobQueryException(
                        "Category must be one of: " + String.join(", ", CategoryClassifier.CATEGORIES) + "."));
    }

    /** Also used for the facet params, which follow the same rules. */
    static String place(String value, String name) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_PLACE) {
            throw new InvalidJobQueryException("Parameter " + name + " must be 1 to " + MAX_PLACE + " characters.");
        }
        return trimmed;
    }

    private static String text(String value, String name) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_TEXT) {
            throw new InvalidJobQueryException("Parameter " + name + " must be 1 to " + MAX_TEXT + " characters.");
        }
        return trimmed;
    }

    private static String choice(String value, Set<String> allowed, String message) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new InvalidJobQueryException(message);
        }
        return normalized;
    }

    private static Integer postedWithin(String value) {
        if (value == null) {
            return null;
        }
        try {
            int days = Integer.parseInt(value.trim());
            if (POSTED_WITHIN_DAYS.contains(days)) {
                return days;
            }
        } catch (NumberFormatException ex) {
            // Falls through to the same 400 as an unsupported number.
        }
        throw new InvalidJobQueryException("Posted within must be 1, 7 or 30 days.");
    }

    private static int offset(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            int offset = Integer.parseInt(value.trim());
            if (offset >= 0) {
                return offset;
            }
        } catch (NumberFormatException ex) {
            // Falls through to the same 400 as a negative number.
        }
        throw new InvalidJobQueryException("Offset must be 0 or more.");
    }
}
