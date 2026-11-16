package com.careercopilot.agent;

public class AgentNoResumeException extends RuntimeException {

    public AgentNoResumeException() {
        super("Upload a resume first.");
    }
}
