package com.careercopilot.applications;

import com.careercopilot.jobs.Company;
import com.careercopilot.jobs.CompanyRepository;
import com.careercopilot.jobs.Job;
import com.careercopilot.jobs.JobRepository;
import com.careercopilot.profiles.Profile;
import com.careercopilot.profiles.ProfileNotFoundException;
import com.careercopilot.profiles.ProfileRepository;
import com.careercopilot.resumes.ResumeNotFoundException;
import com.careercopilot.resumes.ResumeRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    private final ProfileRepository profiles;
    private final ApplicationRepository applications;
    private final JobRepository jobs;
    private final CompanyRepository companies;
    private final ResumeRepository resumes;

    public ApplicationService(ProfileRepository profiles, ApplicationRepository applications, JobRepository jobs,
            CompanyRepository companies, ResumeRepository resumes) {
        this.profiles = profiles;
        this.applications = applications;
        this.jobs = jobs;
        this.companies = companies;
        this.resumes = resumes;
    }

    @Transactional
    public ApplicationResponse create(Long userId, ApplicationCreateRequest request) {
        Profile profile = profiles.findByUserId(userId).orElseThrow(ProfileNotFoundException::new);
        String status = request.status() == null ? "draft" : request.status();
        Job job = jobs.findById(request.jobId())
                .filter(found -> visibleTo(found, profile.getId()))
                .orElseThrow(ApplicationNotFoundException::new);
        if (request.resumeId() != null
                && resumes.findByIdAndProfileId(request.resumeId(), profile.getId()).isEmpty()) {
            throw new ResumeNotFoundException();
        }
        if (applications.existsByProfileIdAndJobId(profile.getId(), job.getId())) {
            throw new ApplicationAlreadyExistsException();
        }

        Application application = new Application(profile.getId(), job.getId());
        application.setResumeId(request.resumeId());
        application.setStatus(status);
        application.setCoverLetter(request.coverLetter());
        application.setSubmittedBy("user");
        if ("applied".equals(status)) {
            application.setSubmittedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        }
        Application saved;
        try {
            saved = applications.saveAndFlush(application);
        } catch (DataIntegrityViolationException e) {
            // A parallel request created the same (profile, job) row first.
            throw new ApplicationAlreadyExistsException();
        }
        log.info("Application {} created for profile {}", saved.getId(), profile.getId());
        return ApplicationResponse.from(saved, job.getTitle(), companyName(job, loadCompanies(List.of(job))));
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> list(Long userId) {
        Profile profile = profiles.findByUserId(userId).orElse(null);
        if (profile == null) {
            return List.of();
        }
        List<Application> rows = applications.findByProfileIdOrderByCreatedAtDescIdDesc(profile.getId());
        if (rows.isEmpty()) {
            return List.of();
        }
        // Two batch queries (jobs, then companies) instead of one per row.
        Map<Long, Job> jobsById = jobs.findAllById(rows.stream().map(Application::getJobId).distinct().toList())
                .stream().collect(Collectors.toMap(Job::getId, job -> job));
        Map<Long, Company> companiesById = loadCompanies(jobsById.values());
        return rows.stream().map(row -> toResponse(row, jobsById.get(row.getJobId()), companiesById)).toList();
    }

    @Transactional
    public ApplicationResponse update(Long userId, Long id, ApplicationUpdateRequest request) {
        Profile profile = profiles.findByUserId(userId).orElseThrow(ApplicationNotFoundException::new);
        Application application = applications.findByIdAndProfileId(id, profile.getId())
                .orElseThrow(ApplicationNotFoundException::new);
        if (request.status() != null) {
            application.setStatus(request.status());
            if ("applied".equals(request.status()) && application.getSubmittedAt() == null) {
                application.setSubmittedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
                application.setSubmittedBy("user");
            }
        }
        if (request.coverLetter() != null) {
            application.setCoverLetter(request.coverLetter());
        }
        Application saved = applications.saveAndFlush(application);
        Job job = jobs.findById(saved.getJobId()).orElse(null);
        Map<Long, Company> companiesById = job == null ? Map.of() : loadCompanies(List.of(job));
        return toResponse(saved, job, companiesById);
    }

    /** Scraped jobs have no owner; a pasted job is visible only to the profile that created it. */
    private static boolean visibleTo(Job job, Long profileId) {
        return job.getCreatedByProfileId() == null || job.getCreatedByProfileId().equals(profileId);
    }

    private Map<Long, Company> loadCompanies(Collection<Job> jobList) {
        List<Long> ids = jobList.stream().map(Job::getCompanyId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Company> map = new HashMap<>();
        companies.findAllById(ids).forEach(company -> map.put(company.getId(), company));
        return map;
    }

    private static String companyName(Job job, Map<Long, Company> companiesById) {
        Company company = job.getCompanyId() == null ? null : companiesById.get(job.getCompanyId());
        return company == null ? null : company.getName();
    }

    private static ApplicationResponse toResponse(Application row, Job job, Map<Long, Company> companiesById) {
        return ApplicationResponse.from(row, job == null ? null : job.getTitle(),
                job == null ? null : companyName(job, companiesById));
    }
}
