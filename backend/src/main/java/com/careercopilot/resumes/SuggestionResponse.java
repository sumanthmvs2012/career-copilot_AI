package com.careercopilot.resumes;

/** type is add_skill, near_match, add_keyword, headline, location or general. */
public record SuggestionResponse(String type, String text) {
}
