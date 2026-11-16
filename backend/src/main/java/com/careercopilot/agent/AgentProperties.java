package com.careercopilot.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings for the draft agent. It is off by default; the minimum score is 70 (decision 2026-11-16). */
@ConfigurationProperties(prefix = "app.agent")
public record AgentProperties(Boolean enabled, Integer minScore, String cron) {

    public static final int DEFAULT_MIN_SCORE = 70;

    public int minScoreOrDefault() {
        return minScore == null ? DEFAULT_MIN_SCORE : minScore;
    }
}
