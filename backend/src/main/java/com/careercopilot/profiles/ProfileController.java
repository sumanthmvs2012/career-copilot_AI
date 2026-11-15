package com.careercopilot.profiles;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The user id always comes from the token, so a user can only reach their own profile. */
@RestController
@RequestMapping("/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ProfileResponse get(@AuthenticationPrincipal Jwt jwt) {
        return ProfileResponse.from(profileService.getProfile(userId(jwt)));
    }

    @PutMapping
    public ProfileResponse put(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileRequest request) {
        return ProfileResponse.from(profileService.upsertProfile(userId(jwt), request));
    }

    private static Long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
