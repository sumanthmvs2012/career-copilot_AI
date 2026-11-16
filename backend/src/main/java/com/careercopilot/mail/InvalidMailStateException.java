package com.careercopilot.mail;

/** The OAuth state was tampered with, expired or has the wrong purpose. */
public class InvalidMailStateException extends RuntimeException {

    public InvalidMailStateException() {
        super("The sign-in link is invalid or expired.");
    }
}
