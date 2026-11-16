package com.careercopilot.matches;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Picks frequent words from a job description that the resume never uses, skipping filler and known skills. */
final class KeywordFinder {

    static final int MIN_OCCURRENCES = 2;

    private static final Pattern WORD = Pattern.compile("\\p{L}[\\p{L}\\p{N}]{2,}");

    // Everyday English plus words that appear in almost every job post and say nothing about fit.
    private static final Set<String> STOP_WORDS = Set.copyOf(java.util.Arrays.asList(
            "the", "and", "for", "with", "that", "this", "you", "your", "our", "are", "will", "from", "have", "has",
            "had", "was", "were", "been", "being", "not", "but", "all", "any", "can", "may", "must", "should",
            "would", "could", "who", "what", "when", "where", "why", "how", "which", "while", "their", "they",
            "them", "its", "his", "her", "she", "him", "than", "then", "also", "such", "other", "into", "out",
            "about", "over", "under", "more", "most", "less", "least", "very", "well", "just", "like", "both",
            "each", "every", "some", "within", "across", "through", "between", "including", "include", "includes",
            "etc", "per", "via", "using", "use", "used", "able", "ability", "strong", "good", "great", "excellent",
            "experience", "experiences", "experienced", "year", "years", "work", "works", "working", "worked",
            "team", "teams", "role", "roles", "job", "jobs", "position", "company", "companies", "looking", "join",
            "joining", "help", "helps", "helping", "make", "making", "build", "building", "develop", "developing",
            "development", "skills", "skill", "knowledge", "understanding", "required", "requirements",
            "requirement", "preferred", "plus", "nice", "bonus", "responsibilities", "responsibility", "qualifications",
            "benefits", "salary", "equal", "opportunity", "employer", "apply", "application", "candidate",
            "candidates", "people", "environment", "new", "high", "best", "own", "need", "needs", "needed", "want",
            "wants", "get", "getting", "take", "part", "day", "time", "ensure", "provide", "support", "supports",
            "related", "relevant", "similar", "based", "across", "world", "global", "mission", "values", "culture",
            "free", "full", "remote", "hybrid", "onsite", "location", "locations", "employment", "status",
            "senior", "junior", "lead", "principal", "staff", "head", "manager", "director", "associate", "intern",
            "engineer", "engineers", "engineering", "developer", "developers", "analyst", "specialist", "consultant",
            "solutions", "solution", "targets", "target", "customers", "customer", "client", "clients", "scaling",
            "scale", "operating", "operations", "operational", "growth", "management", "manage", "managing",
            "revenue", "business", "businesses", "product", "products", "service", "services", "market", "markets",
            "industry", "organization", "organizations", "stakeholders", "stakeholder", "partners", "partner",
            "partnerships", "strategy", "strategic", "drive", "driving", "deliver", "delivery", "leading",
            "leadership", "responsible", "impact", "key", "projects", "project", "process", "processes",
            "tools", "tool", "platform", "systems", "system", "data", "sales", "growing", "grow",
            "opportunities", "challenges", "problems", "solve", "solving", "improve", "improving", "create",
            "creating", "inclusive", "diverse", "diversity", "passion", "passionate", "ideal",
            "successful", "success", "goals", "goal", "level", "levels", "cross", "functional", "collaborate",
            "collaboration", "communication", "communicate", "excited", "seeking", "offer", "offers", "today",
            "future", "approach", "approaches", "quality", "performance", "ownership",
            "career", "careers", "life", "employees", "employee", "office", "offices", "pay", "compensation",
            "range", "ranges", "eligible", "eligibility", "authorization", "authorized", "visa", "sponsorship",
            "disability", "veteran", "gender", "race", "religion", "orientation", "accommodation", "accommodations"));

    private KeywordFinder() {
    }

    /** Up to max keywords that occur at least twice in the job text and not at all in the resume text. */
    static List<String> missing(String jobText, String resumeText, Predicate<String> isSkillWord, int max) {
        return missing(jobText, resumeText, isSkillWord, max, null);
    }

    /** As above, never returning a word of {@code excludedText} (the job title and company name). */
    static List<String> missing(String jobText, String resumeText, Predicate<String> isSkillWord, int max,
            String excludedText) {
        if (jobText == null || jobText.isBlank()) {
            return List.of();
        }
        Set<String> resumeKeys = new HashSet<>();
        for (String word : words(resumeText)) {
            resumeKeys.add(key(word));
        }
        Set<String> excludedKeys = new HashSet<>();
        for (String word : words(excludedText)) {
            excludedKeys.add(key(word));
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> display = new LinkedHashMap<>();
        for (String word : words(jobText)) {
            String key = key(word);
            if (STOP_WORDS.contains(word) || STOP_WORDS.contains(key) || isSkillWord.test(word)
                    || isSkillWord.test(key) || resumeKeys.contains(key) || excludedKeys.contains(key)) {
                continue;
            }
            counts.merge(key, 1, Integer::sum);
            display.putIfAbsent(key, word);
        }
        // The sort is stable, so equally frequent words keep the order they first appear in.
        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(counts.entrySet());
        ranked.removeIf(entry -> entry.getValue() < MIN_OCCURRENCES);
        ranked.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return ranked.stream().limit(max).map(entry -> display.get(entry.getKey())).toList();
    }

    private static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        if (text == null) {
            return words;
        }
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            words.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return words;
    }

    /** Folds a plural onto its singular so "services" in a job counts as "service" on the resume. */
    private static String key(String word) {
        if (word.length() > 4 && word.endsWith("s") && !word.endsWith("ss")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }
}
