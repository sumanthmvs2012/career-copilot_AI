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
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final ClaudeInterviewQuestions claude;
    private final TransactionTemplate transactions;

    public InterviewQuestionService(ProfileRepository profiles, JobRepository jobs, CompanyRepository companies,
            ResumeRepository resumes, InterviewQuestionRepository questions, InterviewQuestionGenerator generator,
            MatchService matchService, SkillExtractor extractor,
            InterviewQuestionWriter writer, ClaudeInterviewQuestions claude,
            PlatformTransactionManager transactionManager) {
        this.profiles = profiles;
        this.jobs = jobs;
        this.companies = companies;
        this.resumes = resumes;
        this.questions = questions;
        this.generator = generator;
        this.matchService = matchService;
        this.extractor = extractor;
        this.writer = writer;
        this.claude = claude;
        this.transactions = new TransactionTemplate(transactionManager);
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

    /**
     * Replaces the stored list with a fresh one built from the current resume and job. The new list is generated
     * first (a Claude call can take a while), then the delete and insert run in one short transaction.
     */
    public InterviewQuestionsResponse regenerate(Long userId, Long jobId) {
        Profile profile = profiles.findByUserId(userId).orElseThrow(ProfileNotFoundException::new);
        Job job = matchService.visibleJob(profile, jobId);
        List<InterviewQuestion> rows = generate(profile, job);
        List<InterviewQuestion> saved = transactions.execute(status -> {
            questions.deleteByProfileIdAndJobId(profile.getId(), job.getId());
            questions.flush();
            return writer.saveAll(rows);
        });
        return respond(job, saved);
    }

    /** Claude's questions when available, else (or on any failure) the rule-based list. */
    private List<InterviewQuestion> generate(Profile profile, Job job) {
        String company = companyName(job);
        List<String> jobSkills = CompanySkills.exclude(jobSkills(job), company);
        Optional<Resume> base = resumes.findByProfileIdAndIsBaseTrue(profile.getId());
        List<String> resumeSkills = base.map(this::resumeSkills).orElse(null);
        InterviewQuestionGenerator.Input input = new InterviewQuestionGenerator.Input(job.getTitle(), company,
                jobSkills, resumeSkills);
        List<Generated> rules = generator.generate(input);
        // Resume text only goes to Claude when a key is set; it is never logged.
        List<Generated> generated = claude.generate(profile.getUserId(), input, job.getDescription(),
                base.map(Resume::getParsedText).orElse(null), rules).orElse(rules);
        String source = generated == rules ? "rules" : "claude";

        List<InterviewQuestion> rows = new ArrayList<>();
        for (int i = 0; i < generated.size(); i++) {
            rows.add(new InterviewQuestion(profile.getId(), job.getId(), generated.get(i).question(),
                    generated.get(i).category(), i));
        }
        log.info("Interview questions generated for profile {} and job {}: {} questions ({})", profile.getId(),
                job.getId(), rows.size(), source);
        return rows;
    }

    private List<InterviewQuestion> generateAndStore(Profile profile, Job job) {
        List<InterviewQuestion> rows = generate(profile, job);
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
