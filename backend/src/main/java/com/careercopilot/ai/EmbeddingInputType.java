package com.careercopilot.ai;

/** Voyage embeds queries and documents slightly differently, so callers say which one they have. */
public enum EmbeddingInputType {
    DOCUMENT("document"),
    QUERY("query");

    private final String wireValue;

    EmbeddingInputType(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
