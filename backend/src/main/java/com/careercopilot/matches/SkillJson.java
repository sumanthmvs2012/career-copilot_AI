package com.careercopilot.matches;

import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Reads and writes the JSON array strings kept in the TEXT columns (skills, matched skills, ...). */
public final class SkillJson {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private SkillJson() {
    }

    public static String write(Object list) {
        return MAPPER.writeValueAsString(list);
    }

    /** A null or unreadable column reads as an empty list, never as an error. */
    public static List<String> readStrings(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    public static List<NearMatch> readNearMatches(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<NearMatch>>() {
            });
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
