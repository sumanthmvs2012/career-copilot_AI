package com.careercopilot.mail;

/** GOOGLE_* or MAIL_TOKEN_KEY settings are missing (HTTP 503). */
public class MailNotConfiguredException extends RuntimeException {

    public MailNotConfiguredException() {
        super("Mailbox connection is not configured.");
    }
}
