package com.careercopilot.resumes;

/** The file is not a PDF or DOCX (by extension or file signature). */
public class InvalidResumeFileException extends RuntimeException {

    public InvalidResumeFileException() {
        super("Upload a PDF or DOCX file.");
    }
}
