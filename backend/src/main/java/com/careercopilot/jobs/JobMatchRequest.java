package com.careercopilot.jobs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record JobMatchRequest(
        @Size(max = 200, message = "Title must be 200 characters or fewer.")
        String title,

        @Size(max = 200, message = "Company name must be 200 characters or fewer.")
        String companyName,

        @NotBlank(message = "Paste the job description (20 to 20000 characters).")
        @Size(min = 20, max = 20000, message = "Paste the job description (20 to 20000 characters).")
        String description,

        @Size(max = 200, message = "Location must be 200 characters or fewer.")
        String location,

        @Pattern(regexp = "remote|hybrid|onsite", message = "Remote type must be remote, hybrid or onsite.")
        String remoteType,

        @Size(max = 1000, message = "Job link must be 1000 characters or fewer.")
        String sourceUrl) {
}
