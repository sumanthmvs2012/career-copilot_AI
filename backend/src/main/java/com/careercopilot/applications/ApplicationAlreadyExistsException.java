package com.careercopilot.applications;

public class ApplicationAlreadyExistsException extends RuntimeException {

    public ApplicationAlreadyExistsException() {
        super("You already track this job.");
    }
}
