package com.careercopilot.resumes;

public class ResumeTooLargeException extends RuntimeException {

    public ResumeTooLargeException() {
        super("The file must be 5 MB or smaller.");
    }
}
