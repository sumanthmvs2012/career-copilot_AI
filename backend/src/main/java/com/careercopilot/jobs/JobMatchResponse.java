package com.careercopilot.jobs;

/** match stays null until the AI module ships (Phase 5). */
public record JobMatchResponse(JobResponse job, Object match, String message) {
}
