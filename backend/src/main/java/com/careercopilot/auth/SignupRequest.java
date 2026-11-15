package com.careercopilot.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 320, message = "Email is too long.")
        String email,

        // ASSUMPTION: SECURITY.md has no password policy yet; 8 matches the frontend.
        // 72 is the BCrypt input limit.
        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters.")
        String password) {

    @Override
    public String toString() {
        // Keeps the password out of any accidental logging of the request.
        return "SignupRequest[email=***, password=***]";
    }
}
