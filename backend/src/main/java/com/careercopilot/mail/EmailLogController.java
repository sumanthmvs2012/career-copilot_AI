package com.careercopilot.mail;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EmailLogController {

    private final EmailLogService service;

    public EmailLogController(EmailLogService service) {
        this.service = service;
    }

    @GetMapping("/applications/{id:\\d+}/emails")
    public List<EmailLogResponse> emails(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.forApplication(Long.parseLong(jwt.getSubject()), id);
    }
}
