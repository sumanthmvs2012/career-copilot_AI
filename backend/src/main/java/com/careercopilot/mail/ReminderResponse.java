package com.careercopilot.mail;

import java.time.Instant;

public record ReminderResponse(Long id, Long applicationId, String jobTitle, String company, Instant dueAt) {
}
