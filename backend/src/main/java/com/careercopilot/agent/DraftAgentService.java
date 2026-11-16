package com.careercopilot.agent;

import com.careercopilot.applications.ApplicationAlreadyExistsException;
import com.careercopilot.applications.ApplicationCreateRequest;
import com.careercopilot.applications.ApplicationRepository;
import com.careercopilot.applications.ApplicationService;
import com.careercopilot.jobs.Company;
import com.careercopilot.jobs.CompanyRepository;
import com.careercopilot.jobs.Job;
import com.careercopilot.jobs.JobRepository;
import com.careercopilot.matches.MatchAnalysis;
import com.careercopilot.matches.MatchService;
import com.careercopilot.profiles.Profile;
import com.careercopilot.profiles.ProfileNotFoundException;
import com.careercopilot.profiles.ProfileRepository;
import com.careercopilot.resumes.Resume;
import com.careercopilot.resumes.ResumeRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Writes application drafts (status "draft") for new scraped postings that match a profile's base resume.
 *
 * <p>It never submits anything. TODO: there is no job-board API integration yet, so even when
 * profile.autoSubmitEnabled is true the agent only writes the draft; ApplicationService.submitAuto is neither
 * called nor changed here. Wire it in only once a real submission channel exists.
 *
 * <p>Deliberately not transactional: each draft is saved in its own transaction (ApplicationService.create),
 * so a lost race on the unique (profile, job) row cannot poison the rest of the run. Logs hold ids and counts only.
 */
@Service
public class DraftAgentService {

    static final int MAX_JOBS_PER_RUN = 200;
    static final Duration FIRST_RUN_LOOKBACK = Duration.ofHours(24);

    private static final Logger log = LoggerFactory.getLogger(DraftAgentService.class);

    private final ProfileRepository profiles;
    private final ResumeRepository resumes;
    private final JobRepository jobs;
    private final CompanyRepository companies;
    private final ApplicationRepository applications;
    private final ApplicationService applicationService;
    private final MatchService matchService;
    private final AgentProperties properties;
    private final Clock clock;

    public DraftAgentService(ProfileRepository profiles, ResumeRepository resumes, JobRepository jobs,
            CompanyRepository companies, ApplicationRepository applications, ApplicationService applicationService,
            MatchService matchService, AgentProperties properties, Clock clock) {
        this.profiles = profiles;
        this.resumes = resumes;
        this.jobs = jobs;
        this.companies = companies;
        this.applications = applications;
        this.applicationService = applicationService;
        this.matchService = matchService;
        this.properties = properties;
        this.clock = clock;
    }

    /** Scheduled entry point: every profile that has a base resume. One failing profile does not stop the rest. */
    public AgentRunResponse run() {
        int considered = 0;
        int created = 0;
        for (Profile profile : profiles.findAllWithBaseResume()) {
            Resume base = resumes.findByProfileIdAndIsBaseTrue(profile.getId()).orElse(null);
            if (base == null) {
                continue;
            }
            try {
                AgentRunResponse result = runFor(profile, base);
                considered += result.considered();
                created += result.draftsCreated();
            } catch (RuntimeException e) {
                log.error("Draft agent failed for profile {} ({})", profile.getId(), e.getClass().getSimpleName());
            }
        }
        log.info("Draft agent run finished: {} jobs considered, {} drafts created", considered, created);
        return new AgentRunResponse(considered, created);
    }

    /** Manual entry point: only the caller's own profile, found from the token's user id. */
    public AgentRunResponse runForUser(Long userId) {
        Profile profile = profiles.findByUserId(userId).orElseThrow(ProfileNotFoundException::new);
        Resume base = resumes.findByProfileIdAndIsBaseTrue(profile.getId()).orElseThrow(AgentNoResumeException::new);
        return runFor(profile, base);
    }

    private AgentRunResponse runFor(Profile profile, Resume base) {
        Instant now = clock.instant();
        Instant since = profile.getLastAgentRunAt() != null ? profile.getLastAgentRunAt()
                : now.minus(FIRST_RUN_LOOKBACK);
        List<Job> candidates = jobs.findByCreatedByProfileIdIsNullAndCreatedAtAfterOrderByCreatedAtAscIdAsc(since,
                PageRequest.of(0, MAX_JOBS_PER_RUN));

        int considered = 0;
        int created = 0;
        int minScore = properties.minScoreOrDefault();
        for (Job job : candidates) {
            if (applications.existsByProfileIdAndJobId(profile.getId(), job.getId())) {
                continue;
            }
            MatchAnalysis analysis = matchService.analyze(profile, job, base);
            considered++;
            if (analysis.score() < minScore) {
                continue;
            }
            String letter = CoverLetterTemplate.build(profile.getFullName(), job.getTitle(), companyName(job),
                    analysis.matchedSkills());
            try {
                applicationService.create(profile.getUserId(),
                        new ApplicationCreateRequest(job.getId(), base.getId(), "draft", letter));
                created++;
            } catch (ApplicationAlreadyExistsException e) {
                // Another run or the user tracked this job first; nothing to do.
            }
        }

        // A full batch means more jobs may be waiting: move the marker only to the last job we looked at.
        boolean capped = candidates.size() >= MAX_JOBS_PER_RUN;
        Instant marker = capped ? candidates.get(candidates.size() - 1).getCreatedAt() : now;
        profiles.updateLastAgentRunAt(profile.getId(), marker);
        log.info("Draft agent for profile {}: {} jobs considered, {} drafts created", profile.getId(), considered,
                created);
        return new AgentRunResponse(considered, created);
    }

    private String companyName(Job job) {
        if (job.getCompanyId() == null) {
            return null;
        }
        return companies.findById(job.getCompanyId()).map(Company::getName).orElse(null);
    }
}
