package com.careercopilot.mail;

/** Sync was asked for without an active connection (HTTP 409). */
public class MailNotConnectedException extends RuntimeException {

    public MailNotConnectedException() {
        super("Connect your mailbox first.");
    }
}
