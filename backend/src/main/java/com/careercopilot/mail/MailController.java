package com.careercopilot.mail;

import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The user id always comes from the token, except in the callback: the browser arrives from Google without an
 * Authorization header, so the signed state is the only proof of who started the connection.
 */
@RestController
@RequestMapping("/mail-connections")
public class MailController {

    private final MailConnectionService service;
    private final MailProperties properties;

    public MailController(MailConnectionService service, MailProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @GetMapping
    public MailStatusResponse status(@AuthenticationPrincipal Jwt jwt) {
        return service.status(userId(jwt));
    }

    @PostMapping
    public AuthUrlResponse start(@AuthenticationPrincipal Jwt jwt) {
        return service.start(userId(jwt));
    }

    /** Public path (see SecurityConfig). Always answers with a redirect, never with JSON. */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
            @RequestParam(required = false) String state, @RequestParam(required = false) String error) {
        boolean connected = service.complete(code, state, error);
        String base = properties.frontendUrl().replaceAll("/+$", "");
        URI target = URI.create(base + "/dashboard?mail=" + (connected ? "connected" : "error"));
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, target.toString()).build();
    }

    @PostMapping("/sync")
    public SyncResponse sync(@AuthenticationPrincipal Jwt jwt) {
        MailSyncService.SyncResult result = service.sync(userId(jwt));
        return new SyncResponse(result.checked(), result.newReplies());
    }

    @DeleteMapping("/{id:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        service.disconnect(userId(jwt), id);
    }

    private static Long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
