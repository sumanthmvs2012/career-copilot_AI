package com.careercopilot.profiles;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The booleans are wrapped so a missing JSON field arrives as null and is treated as false. */
public record ProfileRequest(
        @NotBlank(message = "Full name is required.")
        @Size(max = 200, message = "Full name must be 200 characters or fewer.")
        String fullName,

        @Size(max = 200, message = "Headline must be 200 characters or fewer.")
        String headline,

        @Size(max = 200, message = "Location must be 200 characters or fewer.")
        String location,

        Boolean remoteOk,

        Boolean autoSubmitEnabled) {
}
