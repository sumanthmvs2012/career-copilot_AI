package com.careercopilot.applications;

public class ApplicationNotDraftException extends RuntimeException {

    public ApplicationNotDraftException() {
        super("Only a draft can be submitted.");
    }
}
