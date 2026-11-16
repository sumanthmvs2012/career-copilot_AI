package com.careercopilot.applications;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The user id always comes from the token, so a user can only reach their own applications. */
@RestController
@RequestMapping("/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ApplicationCreateRequest request) {
        return applicationService.create(userId(jwt), request);
    }

    @GetMapping
    public List<ApplicationResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return applicationService.list(userId(jwt));
    }

    @PutMapping("/{id}")
    public ApplicationResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody ApplicationUpdateRequest request) {
        return applicationService.update(userId(jwt), id, request);
    }

    /** The body is optional: a cover letter in it is saved first, then the draft is submitted. */
    @PostMapping("/{id}/submit")
    public ApplicationResponse submit(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody(required = false) ApplicationSubmitRequest request) {
        return applicationService.submit(userId(jwt), id, request);
    }

    private static Long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
