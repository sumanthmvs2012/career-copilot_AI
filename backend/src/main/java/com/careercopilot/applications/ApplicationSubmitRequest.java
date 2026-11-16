package com.careercopilot.applications;

import jakarta.validation.constraints.Size;

/** The whole body is optional; a cover letter sent here is saved just before the application is submitted. */
public record ApplicationSubmitRequest(
        @Size(max = 10000, message = "Cover letter must be 10000 characters or fewer.")
        String coverLetter) {
}
