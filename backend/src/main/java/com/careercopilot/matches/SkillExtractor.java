package com.careercopilot.matches;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Finds known skills in free text using the skills.json dictionary. No AI: plain word-boundary matching,
 * so "Java" is not found inside "JavaScript" and "C++", "C#", ".NET" and "Node.js" work.
 */
@Component
public class SkillExtractor {

    static final String RESOURCE = "skills.json";

    /** One entry of skills.json; aliases and related may be missing in the file. */
    record Entry(String name, List<String> aliases, List<String> related) {
    }

    private record Term(String text, String skill) {
    }

    private record Hit(int start, int end, String skill) {
    }

    private final List<Term> terms = new ArrayList<>();
    private final Map<String, Set<String>> related = new HashMap<>();
    private final Set<String> vocabulary = new HashSet<>();

    @Autowired
    public SkillExtractor() {
        this(loadDictionary());
    }

    SkillExtractor(List<Entry> entries) {
        Set<String> names = new HashSet<>();
        for (Entry entry : entries) {
            names.add(entry.name());
        }
        for (Entry entry : entries) {
            addTerms(entry);
            for (String other : orEmpty(entry.related())) {
                // Related is symmetric: if React lists React Native, React Native also relates to React.
                if (names.contains(other) && !other.equals(entry.name())) {
                    related.computeIfAbsent(entry.name(), key -> new LinkedHashSet<>()).add(other);
                    related.computeIfAbsent(other, key -> new LinkedHashSet<>()).add(entry.name());
                }
            }
        }
    }

    /** Canonical skill names found in the text, without duplicates, in order of first appearance. */
    public List<String> extract(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String lower = normalise(text);
        List<Hit> hits = new ArrayList<>();
        for (Term term : terms) {
            int from = 0;
            int at;
            while ((at = lower.indexOf(term.text(), from)) >= 0) {
                int end = at + term.text().length();
                if (startsOnBoundary(lower, at) && endsOnBoundary(lower, end)) {
                    hits.add(new Hit(at, end, term.skill()));
                    from = end;
                } else {
                    from = at + 1;
                }
            }
        }
        // A longer match wins over one inside it, so "React Native" does not also count as "React".
        hits.sort(Comparator.comparingInt(Hit::start).thenComparing(Comparator.comparingInt(Hit::end).reversed()));
        Set<String> found = new LinkedHashSet<>();
        int coveredEnd = -1;
        for (Hit hit : hits) {
            if (hit.end() <= coveredEnd) {
                continue;
            }
            found.add(hit.skill());
            coveredEnd = hit.end();
        }
        return List.copyOf(found);
    }

    /** Skills the dictionary links to the given one (for near matches such as React and React Native). */
    public Set<String> relatedTo(String skill) {
        return related.getOrDefault(skill, Set.of());
    }

    /** True when the word is a one-word skill or alias (java, nodejs), so it is not reported as a missing keyword. */
    public boolean isSkillWord(String word) {
        return vocabulary.contains(word);
    }

    private void addTerms(Entry entry) {
        Set<String> seen = new HashSet<>();
        List<String> texts = new ArrayList<>();
        // One or two letter names (R, C, Go) are everyday text, so only their longer aliases count.
        if (!(entry.name().length() <= 2 && entry.name().chars().allMatch(Character::isLetter))) {
            texts.add(entry.name());
        }
        texts.addAll(orEmpty(entry.aliases()));
        for (String text : texts) {
            String normalised = normalise(text).strip();
            if (!normalised.isEmpty() && seen.add(normalised)) {
                terms.add(new Term(normalised, entry.name()));
            }
        }
        for (String text : texts) {
            // Words inside multi-word skills ("data", "management") are everyday words, so only one-word skills count.
            if (text.strip().contains(" ")) {
                continue;
            }
            for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
                if (word.length() > 1) {
                    vocabulary.add(word);
                }
            }
        }
    }

    /** Lower-cases and turns every whitespace into a space without changing the length, so positions stay valid. */
    private static String normalise(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            chars[i] = Character.isWhitespace(chars[i]) ? ' ' : Character.toLowerCase(chars[i]);
        }
        return new String(chars);
    }

    private static boolean startsOnBoundary(String text, int start) {
        return start == 0 || !isWordChar(text.charAt(start - 1));
    }

    private static boolean endsOnBoundary(String text, int end) {
        if (end >= text.length()) {
            return true;
        }
        char next = text.charAt(end);
        if (Character.isLetter(next) || next == '_') {
            return false;
        }
        // "java.util" is code, "Java." is the end of a sentence.
        return !(next == '.' && end + 1 < text.length() && Character.isLetterOrDigit(text.charAt(end + 1)));
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static List<Entry> loadDictionary() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            return JsonMapper.builder().build().readValue(in, new TypeReference<List<Entry>>() {
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + RESOURCE, e);
        }
    }
}
