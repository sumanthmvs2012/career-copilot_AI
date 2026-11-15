package com.careercopilot.jobs.scraper;

import java.time.Instant;

/** One posting read from a board, already converted to plain text. Nullable fields are simply unknown. */
public record ScrapedJob(
        String externalId,
        String title,
        String companyName,
        String location,
        String remoteType,
        String sourceUrl,
        String description,
        Instant postedAt,
        String department) {
}
