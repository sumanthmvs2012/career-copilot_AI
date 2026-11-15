package com.careercopilot.jobs.scraper;

import java.util.List;

/** One applicant tracking system whose public board API we can read. */
public interface JobSource {

    /** Stored as jobs.source and companies.ats_type, for example "greenhouse". */
    String source();

    /** Makes one request for the board and returns every posting it lists. Throws if the request fails. */
    List<ScrapedJob> fetch(String boardToken);
}
