package com.careercopilot.resumes;

public class ResumeUnreadableException extends RuntimeException {

    public ResumeUnreadableException(Throwable cause) {
        super("We could not read text from this file.", cause);
    }

    public ResumeUnreadableException() {
        super("We could not read text from this file.");
    }
}
