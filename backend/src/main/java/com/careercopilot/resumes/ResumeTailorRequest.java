package com.careercopilot.resumes;

import jakarta.validation.constraints.NotNull;

public record ResumeTailorRequest(@NotNull Long jobId) {
}
