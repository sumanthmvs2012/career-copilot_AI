package com.careercopilot.applications;

public class ApplicationNotFoundException extends RuntimeException {

    public ApplicationNotFoundException() {
        super("Application not found.");
    }
}
