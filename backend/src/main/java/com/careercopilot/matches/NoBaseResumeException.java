package com.careercopilot.matches;

public class NoBaseResumeException extends RuntimeException {

    public NoBaseResumeException() {
        super("Upload a resume first to see your match.");
    }
}
