package com.careercopilot.resumes;

import com.careercopilot.common.storage.StorageException;
import com.careercopilot.common.storage.StorageService;
import com.careercopilot.jobs.Company;
import com.careercopilot.jobs.CompanyRepository;
import com.careercopilot.jobs.Job;
import com.careercopilot.matches.MatchAnalysis;
import com.careercopilot.matches.MatchService;
import com.careercopilot.profiles.Profile;
import com.careercopilot.profiles.ProfileNotFoundException;
import com.careercopilot.profiles.ProfileRepository;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rule-based tailoring and suggestions. Resume text and skills are never logged. */
@Service
public class ResumeTailorService {

    private static final Logger log = LoggerFactory.getLogger(ResumeTailorService.class);

    static final String TEXT_TYPE = "text/plain";
    private static final int MAX_FILE_NAME = 255;

    private final ProfileRepository profiles;
    private final ResumeRepository resumes;
    private final CompanyRepository companies;
    private final MatchService matchService;
    private final ResumeSuggestionService suggestions;
    private final StorageService storage;

    public ResumeTailorService(ProfileRepository profiles, ResumeRepository resumes, CompanyRepository companies,
            MatchService matchService, ResumeSuggestionService suggestions, StorageService storage) {
        this.profiles = profiles;
        this.resumes = resumes;
        this.companies = companies;
        this.matchService = matchService;
        this.suggestions = suggestions;
        this.storage = storage;
    }

    @Transactional
    public TailoredResumeResponse tailor(Long userId, Long resumeId, Long jobId) {
        Profile profile = profiles.findByUserId(userId).orElseThrow(ProfileNotFoundException::new);
        Resume original = resumes.findByIdAndProfileId(resumeId, profile.getId())
                .filter(resume -> resume.getBaseResumeId() == null)
                .orElseThrow(ResumeNotFoundException::new);
        Job job = matchService.visibleJob(profile, jobId);
        MatchAnalysis analysis = matchService.analyze(profile, job, original);
        String companyName = companyName(job);

        String text = TailoredTextBuilder.build(analysis.suggestedHeadline(), analysis.matchedSkills(),
                original.getParsedText());
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        String key = "resumes/" + profile.getId() + "/" + UUID.randomUUID() + ".txt";
        storage.store(key, new ByteArrayInputStream(bytes), bytes.length);

        List<Resume> previous = resumes.findByProfileIdAndBaseResumeIdAndJobId(profile.getId(), original.getId(),
                job.getId());
        Resume saved;
        try {
            if (!previous.isEmpty()) {
                resumes.deleteAll(previous);
                resumes.flush();
            }
            Resume copy = new Resume(profile.getId(), key, fileName(job, companyName), TEXT_TYPE, text);
            copy.setBase(false);
            copy.setBaseResumeId(original.getId());
            copy.setJobId(job.getId());
            copy.setSkills(original.getSkills());
            saved = resumes.save(copy);
        } catch (RuntimeException e) {
            storage.delete(key);
            throw e;
        }
        previous.forEach(old -> deleteQuietly(old.getFilePath()));
        log.info("Resume {} tailored for job {} (profile {}, copy {})", original.getId(), job.getId(),
                profile.getId(), saved.getId());
        return TailoredResumeResponse.from(
                ResumeDetailResponse.from(saved, new ResumeJobLabel(job.getTitle(), companyName)),
                suggestions.suggest(job, analysis));
    }

    /** The resume may be an uploaded one or a tailored copy of the caller. */
    @Transactional
    public SuggestionsResponse suggest(Long userId, Long resumeId, Long jobId) {
        Profile profile = profiles.findByUserId(userId).orElseThrow(ProfileNotFoundException::new);
        Resume resume = resumes.findByIdAndProfileId(resumeId, profile.getId())
                .orElseThrow(ResumeNotFoundException::new);
        Job job = matchService.visibleJob(profile, jobId);
        MatchAnalysis analysis = matchService.analyze(profile, job, resume);
        log.info("Suggestions generated for resume {} and job {}", resume.getId(), job.getId());
        return new SuggestionsResponse(suggestions.suggest(job, analysis));
    }

    private String companyName(Job job) {
        if (job.getCompanyId() == null) {
            return null;
        }
        return companies.findById(job.getCompanyId()).map(Company::getName).orElse(null);
    }

    /** "Tailored - title - company.txt", or the title alone when there is no company. */
    static String fileName(Job job, String company) {
        String title = clean(job.getTitle());
        String name = "Tailored - " + title + (company == null || company.isBlank() ? "" : " - " + clean(company));
        String suffix = ".txt";
        if (name.length() + suffix.length() > MAX_FILE_NAME) {
            name = name.substring(0, MAX_FILE_NAME - suffix.length()).strip();
        }
        return name + suffix;
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n\\t/\\\\]+", " ").strip();
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (StorageException e) {
            log.warn("Could not delete a replaced stored file");
        }
    }
}
