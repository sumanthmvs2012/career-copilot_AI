package com.careercopilot.matches;

import com.careercopilot.jobs.JobLocation;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Compares the profile location with a job's location. Plain text matching, no geocoding. */
final class LocationFitCalculator {

    static final String MATCH = "match";
    static final String PARTIAL = "partial";
    static final String MISMATCH = "mismatch";
    static final String UNKNOWN = "unknown";

    private LocationFitCalculator() {
    }

    static LocationFit evaluate(boolean remoteOk, String profileLocation, String remoteType, String jobLocation,
            List<JobLocation> places) {
        boolean remoteJob = "remote".equalsIgnoreCase(remoteType);
        if (remoteJob && remoteOk) {
            return new LocationFit(MATCH, "This job is remote and you are open to remote work.");
        }
        String mine = blankToNull(profileLocation);
        boolean jobHasPlace = !places.isEmpty() || blankToNull(jobLocation) != null;
        if (mine == null) {
            return new LocationFit(UNKNOWN, remoteJob
                    ? "This job is remote. Turn on remote work in your profile to count it as a fit."
                    : "Add your location to your profile to see how this job fits.");
        }
        if (!jobHasPlace) {
            return new LocationFit(UNKNOWN, remoteJob
                    ? "This job is remote. Turn on remote work in your profile to count it as a fit."
                    : "This job does not list a location.");
        }

        String fit = places.isEmpty() ? compareText(mine, jobLocation) : comparePlaces(mine, places);
        String where = jobLocation != null && !jobLocation.isBlank() ? jobLocation.strip() : describe(places.get(0));
        return switch (fit) {
            case MATCH -> new LocationFit(MATCH, cap("The job location (" + where + ") matches your location."));
            case PARTIAL -> new LocationFit(PARTIAL,
                    cap("The job is in the same region as you (" + where + "), but not the same city."));
            default -> new LocationFit(MISMATCH, remoteJob
                    ? "This job is remote and your profile is not open to remote work."
                    : cap("The job location (" + where + ") is different from yours."));
        };
    }

    private static String comparePlaces(String mine, List<JobLocation> places) {
        String best = MISMATCH;
        for (JobLocation place : places) {
            if (containsWord(mine, place.getCity())) {
                return MATCH;
            }
            if (containsWord(mine, place.getState()) || containsWord(mine, place.getCountry())) {
                best = PARTIAL;
            }
        }
        return best;
    }

    /** Profile locations read "City, State, Country"; the first part is the city. */
    private static String compareText(String mine, String jobLocation) {
        String[] parts = mine.split("[,;/]");
        String best = MISMATCH;
        for (int i = 0; i < parts.length; i++) {
            if (containsWord(jobLocation, parts[i].strip())) {
                if (i == 0) {
                    return MATCH;
                }
                best = PARTIAL;
            }
        }
        return best;
    }

    private static boolean containsWord(String haystack, String needle) {
        if (haystack == null || needle == null || needle.strip().length() < 2) {
            return false;
        }
        String pattern = "(?<![\\p{L}\\p{N}])" + Pattern.quote(needle.strip().toLowerCase(Locale.ROOT))
                + "(?![\\p{L}\\p{N}])";
        return Pattern.compile(pattern).matcher(haystack.toLowerCase(Locale.ROOT)).find();
    }

    private static String describe(JobLocation place) {
        StringBuilder text = new StringBuilder();
        for (String part : new String[] {place.getCity(), place.getState(), place.getCountry()}) {
            if (part != null && !part.isBlank()) {
                text.append(text.length() == 0 ? "" : ", ").append(part);
            }
        }
        return text.toString();
    }

    private static String cap(String note) {
        return note.length() > 300 ? note.substring(0, 300) : note;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
