package com.careercopilot.profiles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final ProfileRepository profiles;

    public ProfileService(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public Profile getProfile(Long userId) {
        return profiles.findByUserId(userId).orElseThrow(ProfileNotFoundException::new);
    }

    @Transactional
    public Profile upsertProfile(Long userId, ProfileRequest request) {
        Profile profile = profiles.findByUserId(userId).orElse(null);
        boolean created = profile == null;
        if (created) {
            profile = new Profile(userId);
        }
        profile.setFullName(request.fullName().trim());
        profile.setHeadline(blankToNull(request.headline()));
        profile.setLocation(blankToNull(request.location()));
        profile.setRemoteOk(Boolean.TRUE.equals(request.remoteOk()));
        profile.setAutoSubmitEnabled(Boolean.TRUE.equals(request.autoSubmitEnabled()));
        Profile saved = profiles.save(profile);
        if (created) {
            log.info("Profile created for user {}", userId);
        }
        return saved;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
