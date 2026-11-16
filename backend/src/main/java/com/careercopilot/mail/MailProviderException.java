package com.careercopilot.mail;

/** The mail provider failed. The message is ours and never carries a token, a code or a response body. */
public class MailProviderException extends RuntimeException {

    public MailProviderException(String message) {
        super(message);
    }
}
