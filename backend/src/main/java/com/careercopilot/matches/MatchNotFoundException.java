package com.careercopilot.matches;

public class MatchNotFoundException extends RuntimeException {

    public MatchNotFoundException() {
        super("No match yet for this job.");
    }
}
