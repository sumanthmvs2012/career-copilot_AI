package com.careercopilot.interviews;

import com.careercopilot.interviews.InterviewQuestionGenerator.Generated;
import com.careercopilot.jobs.Company;
import com.careercopilot.jobs.CompanyRepository;
import com.careercopilot.jobs.Job;
import com.careercopilot.jobs.JobRepository;
import com.careercopilot.matches.CompanySkills;
import com.careercopilot.matches.MatchService;
import com.careercopilot.matches.SkillExtractor;
import com.careercopilot.matches.SkillJson;
import com.careercopilot.profiles.Profile;
import com.careercopilot.profiles.ProfileNotFoundException;
import com.careercopilot.profiles.ProfileRepository;
import com.careercopilot.resumes.Resume;
import com.careercopilot.resumes.ResumeRepository;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Questions are stored per profile and job, so repeat calls return the same list until it is regenerated. */
@Service
public class InterviewQuestionService {

    private static final Logger log = LoggerFactory.getLogger(InterviewQuestionService.class);

    private final ProfileRepository profiles;
    private final JobRepository jobs;
    private final CompanyRepository companies;
    private final ResumeRepository resumes;
    private final InterviewQuestionRepository questions;
    private final InterviewQuestionGenerator generator;
    private final MatchService matchService;
    private final SkillExtractor extractor;
    private final InterviewQuestionWriter writer;

    public InterviewQuestionService(ProfileRepository profiles, JobRepository jobs, CompanyRepository companies,
            ResumeRepository resumes, InterviewQuestionRepository questions, InterviewQuestionGenerator generator,
            MatchService matchService, SkillExtractor extractor,
            InterviewQuestionWriter writer) {
        this.profiles = profiles;
        this.jobs = jobs;
        this.companies = companies;
        this.resumes = resumes;
        this.questions = questions;
        this.generator = generator;
        this.matchService = matchService;
        this.extractor = extractor;
        this.writer = writer;
    }

    /** Not one big transaction: the list is written by InterviewQuestionWriter so a lost race can be handled. */
    public InterviewQuestionsResponse get(Long userId, Long jobId) {
        Profile profile = profiles.findByUserId(userId).orElseThrow(ProfileNotFoundException::new);
        Job job = matchService.visibleJob(profile, jobId);
        List<InterviewQuestion> stored = questions.findByProfileIdAndJobIdOrderByPositionAscIdAsc(profile.getId(),
                job.getId());
        if (stored.isEmpty()) {
            stored = generateAndStore(profile, job);
        }
        return respond(job, stored);
    }

    /** Replaces the stored list with a fresh one built from the current resume and job. */
    @Transactional
    public InterviewQuestionsResponse regenerate(Long userId, Long jobId) {
        Profile profile = profiles.findByUserId(userId).orElseThrow(ProfileNotFoundException::new);
        Job job = matchService.visibleJob(profile, jobId);
        questions.deleteByProfileIdAndJobId(profile.getId(), job.getId());
        questions.flush();
        return respond(job, generateAndStore(profile, job));
    }

    private List<InterviewQuestion> generateAndStore(Profile profile, Job job) {
        List<String> jobSkills = CompanySkills.exclude(jobSkills(job), companyName(job));
        // Only the base resume's skills are read; its text is never touched here.
        List<String> resumeSkills = resumes.findByProfileIdAndIsBaseTrue(profile.getId())
                .map(this::resumeSkills).orElse(null);
        List<Generated> generated = generator.generate(new InterviewQuestionGenerator.Input(job.getTitle(),
                companyName(job), jobSkills, resumeSkills));

        List<InterviewQuestion> rows = new ArrayList<>();
        for (int i = 0; i < generated.size(); i++) {
            rows.add(new InterviewQuestion(profile.getId(), job.getId(), generated.get(i).question(),
                    generated.get(i).category(), i));
        }
        List<InterviewQuestion> saved;
        try {
            saved = writer.saveAll(rows);
        } catch (DataIntegrityViolationException e) {
            // A simultaneous first request stored the list already: return that one.
            List<InterviewQuestion> existing = questions.findByProfileIdAndJobIdOrderByPositionAscIdAsc(
                    profile.getId(), job.getId());
            if (existing.isEmpty()) {
                throw e;
            }
            return existing;
        }
        log.info("Interview questions generated for profile {} and job {}: {} questions", profile.getId(),
                job.getId(), saved.size());
        return saved;
    }

    private List<String> jobSkills(Job job) {
        if (job.getSkills() == null) {
            job.setSkills(SkillJson.write(extractor.extract(job.getTitle() + "\n" + job.getDescription())));
            jobs.save(job);
        }
        return SkillJson.readStrings(job.getSkills());
    }

    private List<String> resumeSkills(Resume resume) {
        if (resume.getSkills() == null) {
            resume.setSkills(SkillJson.write(extractor.extract(resume.getParsedText())));
            resumes.save(resume);
        }
        return SkillJson.readStrings(resume.getSkills());
    }

    private String companyName(Job job) {
        return job.getCompanyId() == null ? null
                : companies.findById(job.getCompanyId()).map(Company::getName).orElse(null);
    }

    private InterviewQuestionsResponse respond(Job job, List<InterviewQuestion> rows) {
        return new InterviewQuestionsResponse(job.getId(), job.getTitle(), companyName(job),
                rows.stream().map(InterviewQuestionsResponse.QuestionDto::from).toList());
    }
}
