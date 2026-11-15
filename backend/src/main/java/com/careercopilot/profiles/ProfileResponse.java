package com.careercopilot.profiles;

import java.time.Instant;

public record ProfileResponse(
        Long id,
        String fullName,
        String headline,
        String location,
        boolean remoteOk,
        boolean autoSubmitEnabled,
        Instant createdAt,
        Instant updatedAt) {

    public static ProfileResponse from(Profile profile) {
        return new ProfileResponse(
                profile.getId(),
                profile.getFullName(),
                profile.getHeadline(),
                profile.getLocation(),
                profile.isRemoteOk(),
                profile.isAutoSubmitEnabled(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }
}
