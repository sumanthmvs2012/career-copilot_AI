package com.careercopilot.mail;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ReminderUpdateRequest(
        @NotNull(message = "Status is required.")
        @Pattern(regexp = "dismissed", message = "Status must be dismissed.")
        String status) {
}
