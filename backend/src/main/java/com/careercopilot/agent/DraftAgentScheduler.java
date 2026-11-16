package com.careercopilot.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Off unless app.agent.enabled=true (AGENT_ENABLED). Writes drafts only; it never submits. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.agent.enabled", havingValue = "true")
public class DraftAgentScheduler {

    private static final Logger log = LoggerFactory.getLogger(DraftAgentScheduler.class);

    private final DraftAgentService agent;

    public DraftAgentScheduler(DraftAgentService agent) {
        this.agent = agent;
    }

    @Scheduled(cron = "${app.agent.cron:0 15 * * * *}")
    public void run() {
        try {
            agent.run();
        } catch (RuntimeException e) {
            log.error("Draft agent run failed ({})", e.getClass().getSimpleName());
        }
    }
}
