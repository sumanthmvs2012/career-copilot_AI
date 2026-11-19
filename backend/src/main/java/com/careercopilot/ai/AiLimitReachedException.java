package com.careercopilot.ai;

/** The user used all of their AI calls for today. Mapped to 429 "Daily AI limit reached.". */
public class AiLimitReachedException extends RuntimeException {

    public AiLimitReachedException() {
        super("Daily AI limit reached.");
    }
}
