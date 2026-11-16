package com.careercopilot.agent;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The user id always comes from the token, so a caller can only run the agent for their own profile. */
@RestController
@RequestMapping("/agent")
public class AgentController {

    private final DraftAgentService agent;

    public AgentController(DraftAgentService agent) {
        this.agent = agent;
    }

    @PostMapping("/run")
    public AgentRunResponse run(@AuthenticationPrincipal Jwt jwt) {
        return agent.runForUser(Long.parseLong(jwt.getSubject()));
    }
}
