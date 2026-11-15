package com.careercopilot.profiles;

public class ProfileNotFoundException extends RuntimeException {

    public ProfileNotFoundException() {
        super("Profile not found.");
    }
}
