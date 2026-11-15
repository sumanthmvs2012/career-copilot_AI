package com.careercopilot.matches;

/** The job does not exist or is another user's pasted job; both look the same to the caller. */
public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException() {
        super("Job not found.");
    }
}
