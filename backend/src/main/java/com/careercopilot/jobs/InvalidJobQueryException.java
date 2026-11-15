package com.careercopilot.jobs;

/** Thrown for a bad scope or limit on GET /jobs; the handler turns it into a 400. */
public class InvalidJobQueryException extends RuntimeException {

    public InvalidJobQueryException(String message) {
        super(message);
    }
}
