package com.careercopilot.matches;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores a match in its own transaction, so a lost race on the unique (profile, job) row can be caught by the
 * caller and retried as an update instead of leaving the caller's transaction broken.
 */
@Component
public class MatchWriter {

    private final JobMatchRepository matches;

    public MatchWriter(JobMatchRepository matches) {
        this.matches = matches;
    }

    /** One row per profile and job: the old row goes first so the new result carries a fresh timestamp. */
    @Transactional
    public JobMatch replace(JobMatch row) {
        matches.findByProfileIdAndJobId(row.getProfileId(), row.getJobId()).ifPresent(old -> {
            matches.delete(old);
            matches.flush();
        });
        return matches.saveAndFlush(row);
    }

    /** Used after losing a race: the other request's row is updated in place with this result. */
    @Transactional
    public JobMatch overwrite(JobMatch row) {
        JobMatch existing = matches.findByProfileIdAndJobId(row.getProfileId(), row.getJobId()).orElse(null);
        if (existing == null) {
            return matches.saveAndFlush(row);
        }
        existing.setResumeId(row.getResumeId());
        existing.setScore(row.getScore());
        existing.setMatchedSkills(row.getMatchedSkills());
        existing.setMissingSkills(row.getMissingSkills());
        existing.setNearMatchedSkills(row.getNearMatchedSkills());
        existing.setMissingKeywords(row.getMissingKeywords());
        existing.setSuggestedHeadline(row.getSuggestedHeadline());
        existing.setScoreSource(row.getScoreSource());
        existing.setLocationFitStatus(row.getLocationFitStatus());
        existing.setLocationFitNote(row.getLocationFitNote());
        return matches.saveAndFlush(existing);
    }
}
