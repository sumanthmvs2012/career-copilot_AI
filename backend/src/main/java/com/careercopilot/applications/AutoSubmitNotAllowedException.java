package com.careercopilot.applications;

/** The profile has not turned on auto-submit; only the user can do that. Never reaches HTTP. */
public class AutoSubmitNotAllowedException extends RuntimeException {

    public AutoSubmitNotAllowedException() {
        super("Auto-submit is not enabled for this profile.");
    }
}
