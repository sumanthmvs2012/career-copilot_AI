package com.careercopilot.resumes;

public class ResumeLimitReachedException extends RuntimeException {

    public ResumeLimitReachedException() {
        super("You already have 2 resumes. Delete one to upload another.");
    }
}
