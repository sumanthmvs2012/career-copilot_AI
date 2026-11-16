package com.careercopilot.matches;

import com.careercopilot.jobs.Job;
import com.careercopilot.jobs.JobLocation;
import com.careercopilot.jobs.JobLocationRepository;
import com.careercopilot.jobs.JobRepository;
import com.careercopilot.profiles.Profile;
import com.careercopilot.profiles.ProfileNotFoundException;
import com.careercopilot.profiles.ProfileRepository;
import com.careercopilot.resumes.Resume;
import com.careercopilot.resumes.ResumeRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keyword matching of the caller's base resume against a job. Resume text and skills are never logged. */
@Service
public class MatchService {

    static final int MAX_KEYWORDS = 10;
    /** Same title JobService gives a pasted job without one; it says nothing, so it is not a headline. */
    public static final String DEFAULT_TITLE = "Pasted job";

    private static final Logger log = LoggerFactory.getLogger(MatchService.class);

    private final ProfileRepository profiles;
    private final JobRepository jobs;
    private final ResumeRepository resumes;
    private final JobMatchRepository matches;
    private final JobLocationRepository locations;
    private final SkillExtractor extractor;

    public MatchService(ProfileRepository profiles, JobRepository jobs, ResumeRepository resumes,
            JobMatchRepository matches, JobLocationRepository locations, SkillExtractor extractor) {
        this.profiles = profiles;
        this.jobs = jobs;
        this.resumes = resumes;
        this.matches = matches;
        this.locations = locations;
        this.extractor = extractor;
    }

    @Transactional
    public MatchResponse matchJob(Long userId, Long jobId) {
        Profile profile = profiles.findByUserId(userId).orElseThrow(ProfileNotFoundException::new);
        Job job = visibleJob(profile, jobId);
        Resume resume = resumes.findByProfileIdAndIsBaseTrue(profile.getId()).orElseThrow(NoBaseResumeException::new);
        return compute(profile, job, resume);
    }

    /** For a job the caller just pasted: null when there is no base resume to match against. */
    @Transactional
    public MatchResponse matchIfResume(Profile profile, Job job) {
        return resumes.findByProfileIdAndIsBaseTrue(profile.getId())
                .map(resume -> compute(profile, job, resume))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public MatchResponse getStored(Long userId, Long jobId) {
        Profile profile = profiles.findByUserId(userId).orElseThrow(ProfileNotFoundException::new);
        Job job = visibleJob(profile, jobId);
        return matches.findByProfileIdAndJobId(profile.getId(), job.getId())
                .map(MatchResponse::from)
                .orElseThrow(MatchNotFoundException::new);
    }

    /** Scraped jobs have no owner; a pasted job is visible only to the profile that created it. */
    public Job visibleJob(Profile profile, Long jobId) {
        return jobs.findById(jobId)
                .filter(job -> job.getCreatedByProfileId() == null
                        || job.getCreatedByProfileId().equals(profile.getId()))
                .orElseThrow(JobNotFoundException::new);
    }

    /**
     * Computes the match of any resume against a job without saving a match row (only missing skill lists
     * are filled in on the resume and job rows, as in a normal match).
     */
    @Transactional
    public MatchAnalysis analyze(Profile profile, Job job, Resume resume) {
        List<String> resumeSkills = resumeSkills(resume);
        List<String> jobSkills = jobSkills(job);
        Set<String> have = new HashSet<>(resumeSkills);

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<NearMatch> near = new ArrayList<>();
        for (String skill : jobSkills) {
            if (have.contains(skill)) {
                matched.add(skill);
                continue;
            }
            missing.add(skill);
            // The dictionary order decides which related skill is shown when several are on the resume.
            extractor.relatedTo(skill).stream().filter(have::contains).findFirst()
                    .ifPresent(related -> near.add(new NearMatch(skill, related)));
        }
        int score = jobSkills.isEmpty() ? 0 : (int) Math.round(100.0 * matched.size() / jobSkills.size());

        List<String> keywords = KeywordFinder.missing(job.getTitle() + "\n" + job.getDescription(),
                resume.getParsedText(), extractor::isSkillWord, MAX_KEYWORDS);
        List<JobLocation> places = locations.findByJobIdIn(List.of(job.getId()));
        LocationFit fit = LocationFitCalculator.evaluate(profile.isRemoteOk(), profile.getLocation(),
                job.getRemoteType(), job.getLocation(), places);
        return new MatchAnalysis(score, jobSkills, matched, missing, near, keywords, headline(job, profile), fit);
    }

    private MatchResponse compute(Profile profile, Job job, Resume resume) {
        MatchAnalysis analysis = analyze(profile, job, resume);
        int score = analysis.score();
        LocationFit fit = analysis.locationFit();
        List<String> matched = analysis.matchedSkills();
        List<String> missing = analysis.missingSkills();
        List<NearMatch> near = analysis.nearMatchedSkills();
        List<String> keywords = analysis.missingKeywords();

        // One row per profile and job: the old row goes first so the new result carries a fresh timestamp.
        matches.findByProfileIdAndJobId(profile.getId(), job.getId()).ifPresent(old -> {
            matches.delete(old);
            matches.flush();
        });
        JobMatch row = new JobMatch(profile.getId(), job.getId());
        row.setResumeId(resume.getId());
        row.setScore(score);
        row.setMatchedSkills(SkillJson.write(matched));
        row.setMissingSkills(SkillJson.write(missing));
        row.setNearMatchedSkills(SkillJson.write(near));
        row.setMissingKeywords(SkillJson.write(keywords));
        row.setSuggestedHeadline(analysis.suggestedHeadline());
        row.setLocationFitStatus(fit.status());
        row.setLocationFitNote(fit.note());
        JobMatch saved = matches.save(row);

        log.info("Match computed for profile {} and job {}: score {}, {} job skills", profile.getId(), job.getId(),
                score, analysis.jobSkills().size());
        return MatchResponse.from(saved);
    }

    /** Older resumes were uploaded before skills existed, so they are extracted the first time they are needed. */
    private List<String> resumeSkills(Resume resume) {
        if (resume.getSkills() == null) {
            resume.setSkills(SkillJson.write(extractor.extract(resume.getParsedText())));
            resumes.save(resume);
        }
        return SkillJson.readStrings(resume.getSkills());
    }

    private List<String> jobSkills(Job job) {
        if (job.getSkills() == null) {
            job.setSkills(SkillJson.write(extractor.extract(job.getTitle() + "\n" + job.getDescription())));
            jobs.save(job);
        }
        return SkillJson.readStrings(job.getSkills());
    }

    private static String headline(Job job, Profile profile) {
        String title = job.getTitle();
        if (title != null && !title.isBlank() && !DEFAULT_TITLE.equals(title.strip())) {
            return title.strip();
        }
        String own = profile.getHeadline();
        return own == null || own.isBlank() ? null : own.strip();
    }
}
