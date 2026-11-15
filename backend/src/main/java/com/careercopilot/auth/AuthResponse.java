package com.careercopilot.auth;

/** Matches the frontend authService.extractToken contract: {"token": "<jwt>"}. */
public record AuthResponse(String token) {

    @Override
    public String toString() {
        return "AuthResponse[token=***]";
    }
}
