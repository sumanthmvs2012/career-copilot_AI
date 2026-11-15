package com.careercopilot.jobs.scraper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Runs the location reparse once per startup; switch off with app.locations.reparse-on-startup=false. */
@Component
@ConditionalOnProperty(name = "app.locations.reparse-on-startup", havingValue = "true", matchIfMissing = true)
public class JobLocationReparseRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JobLocationReparseRunner.class);

    private final JobLocationReparser reparser;

    public JobLocationReparseRunner(JobLocationReparser reparser) {
        this.reparser = reparser;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            reparser.reparseAll();
        } catch (RuntimeException ex) {
            // Never block startup; only the exception type is logged.
            log.warn("Location reparse failed ({})", ex.getClass().getSimpleName());
        }
    }
}
