package com.careercopilot.agent;

/** considered = new jobs matched against the resume; draftsCreated = drafts written for score >= the minimum. */
public record AgentRunResponse(int considered, int draftsCreated) {
}
