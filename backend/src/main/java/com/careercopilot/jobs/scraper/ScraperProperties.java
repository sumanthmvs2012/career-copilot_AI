package com.careercopilot.jobs.scraper;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** boards is a comma separated list of source:token pairs, for example "greenhouse:stripe,lever:palantir". */
@ConfigurationProperties("app.scraper")
public record ScraperProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("0 0 * * * *") String cron,
        @DefaultValue("") String boards,
        @DefaultValue("PT5S") Duration connectTimeout,
        @DefaultValue("PT20S") Duration readTimeout) {
}
