package com.careercopilot.applications;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ApplicationUpdateRequest(
        @Pattern(regexp = "draft|applied|interviewing|offer|rejected|withdrawn",
                message = "Status must be draft, applied, interviewing, offer, rejected or withdrawn.")
        String status,

        @Size(max = 10000, message = "Cover letter must be 10000 characters or fewer.")
        String coverLetter) {

    @JsonIgnore
    @AssertTrue(message = "Provide a status or a cover letter.")
    public boolean isAnyFieldPresent() {
        return status != null || coverLetter != null;
    }
}
