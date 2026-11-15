package com.careercopilot.jobs;

/** One dropdown option and how many visible jobs it matches. */
public record FacetValue(String value, long count) {
}
