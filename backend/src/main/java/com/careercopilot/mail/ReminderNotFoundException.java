package com.careercopilot.mail;

/** Missing or another user's reminder (HTTP 404). */
public class ReminderNotFoundException extends RuntimeException {

    public ReminderNotFoundException() {
        super("Reminder not found.");
    }
}
