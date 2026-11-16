package com.careercopilot.mail;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The user id always comes from the token, so a user only ever sees and changes their own reminders. */
@RestController
@RequestMapping("/reminders")
public class ReminderController {

    private final ReminderService service;

    public ReminderController(ReminderService service) {
        this.service = service;
    }

    @GetMapping
    public List<ReminderResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(Long.parseLong(jwt.getSubject()));
    }

    @PutMapping("/{id:\\d+}")
    public ReminderResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody ReminderUpdateRequest request) {
        return service.dismiss(Long.parseLong(jwt.getSubject()), id);
    }
}
