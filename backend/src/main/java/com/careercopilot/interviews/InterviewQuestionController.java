package com.careercopilot.interviews;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The user id always comes from the token; jobs are checked with the same visibility rule as matching. */
@RestController
@RequestMapping("/jobs")
public class InterviewQuestionController {

    private final InterviewQuestionService service;

    public InterviewQuestionController(InterviewQuestionService service) {
        this.service = service;
    }

    @GetMapping("/{id:\\d+}/interview-questions")
    public InterviewQuestionsResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.get(Long.parseLong(jwt.getSubject()), id);
    }

    @PostMapping("/{id:\\d+}/interview-questions")
    public InterviewQuestionsResponse regenerate(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.regenerate(Long.parseLong(jwt.getSubject()), id);
    }
}
