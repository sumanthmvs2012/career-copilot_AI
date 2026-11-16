package com.careercopilot.mail;

/** Missing, revoked or another user's connection look the same (HTTP 404). */
public class MailConnectionNotFoundException extends RuntimeException {

    public MailConnectionNotFoundException() {
        super("Mail connection not found.");
    }
}
