package com.careercopilot.matches;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The user id always comes from the token. The numeric id pattern keeps these routes apart from
 * /jobs/match and /jobs/facets, and a non-numeric id is a plain 404.
 */
@RestController
@RequestMapping("/jobs")
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @PostMapping("/{id:\\d+}/match")
    public MatchResponse match(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return matchService.matchJob(Long.parseLong(jwt.getSubject()), id);
    }

    @GetMapping("/{id:\\d+}/match")
    public MatchResponse stored(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return matchService.getStored(Long.parseLong(jwt.getSubject()), id);
    }
}
