package com.careercopilot.ai;

/**
 * An AI provider call failed. The message is ours and never carries a key, a prompt, resume text or a response
 * body; only the HTTP status (0 when there was none) is kept for logging.
 */
public class AiProviderException extends RuntimeException {

    private final int status;

    public AiProviderException(String message, int status) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
