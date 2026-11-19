package com.careercopilot.ai;

/** A text embedding model. Only exists as a bean when VOYAGE_API_KEY is set. */
public interface EmbeddingClient {

    int DIMENSIONS = 1024;

    /** Returns a vector of {@link #DIMENSIONS} floats. Throws AiProviderException (fixed message) on failure. */
    float[] embed(String text, EmbeddingInputType inputType);
}
