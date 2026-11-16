package com.careercopilot.mail;

/** The user already has an active connection (HTTP 409). */
public class MailAlreadyConnectedException extends RuntimeException {

    public MailAlreadyConnectedException() {
        super("Your mailbox is already connected.");
    }
}
