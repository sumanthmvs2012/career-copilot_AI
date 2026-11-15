package com.careercopilot.auth;

/** Thrown for both unknown emails and wrong passwords, so callers cannot tell them apart. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid credentials");
    }
}
