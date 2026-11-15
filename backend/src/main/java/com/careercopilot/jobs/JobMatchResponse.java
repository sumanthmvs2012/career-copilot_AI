package com.careercopilot.jobs;

import com.careercopilot.matches.MatchResponse;

/** match is null when the caller has no base resume to match against. */
public record JobMatchResponse(JobResponse job, MatchResponse match, String message) {
}
