package com.careercopilot.jobs.scraper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Scheduling is switched on only here, so with app.scraper.enabled=false (the default) nothing is scheduled. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scraper.enabled", havingValue = "true")
public class JobScraperScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobScraperScheduler.class);

    private final JobScraperService scraper;

    public JobScraperScheduler(JobScraperService scraper) {
        this.scraper = scraper;
    }

    @Scheduled(cron = "${app.scraper.cron:0 0 * * * *}")
    public void run() {
        try {
            int changed = scraper.scrapeAll();
            log.info("Scraper run finished, {} postings stored", changed);
        } catch (RuntimeException ex) {
            log.error("Scraper run failed ({})", ex.getClass().getSimpleName());
        }
    }
}
