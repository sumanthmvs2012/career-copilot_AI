package com.careercopilot.auth;

/** BCrypt reads only 72 bytes; multi-byte characters can pass the 72-character check and still exceed it. */
public class PasswordTooLongException extends RuntimeException {

    public PasswordTooLongException() {
        super("Password is too long.");
    }
}
