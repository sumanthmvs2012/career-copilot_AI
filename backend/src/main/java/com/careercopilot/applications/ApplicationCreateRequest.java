package com.careercopilot.applications;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ApplicationCreateRequest(
        @NotNull(message = "Job is required.")
        Long jobId,

        Long resumeId,

        @Pattern(regexp = "draft|applied", message = "Status must be draft or applied.")
        String status,

        @Size(max = 10000, message = "Cover letter must be 10000 characters or fewer.")
        String coverLetter) {
}
