package com.careercopilot.resumes;

import com.careercopilot.jobs.Company;
import com.careercopilot.jobs.CompanyRepository;
import com.careercopilot.jobs.Job;
import com.careercopilot.jobs.JobRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Looks up job titles and company names for tailored copies in two queries, however many resumes there are. */
@Component
public class ResumeJobLabels {

    private final JobRepository jobs;
    private final CompanyRepository companies;

    public ResumeJobLabels(JobRepository jobs, CompanyRepository companies) {
        this.jobs = jobs;
        this.companies = companies;
    }

    /** Resume id to label, only for resumes that have a job. */
    @Transactional(readOnly = true)
    public Map<Long, ResumeJobLabel> forResumes(Collection<Resume> resumes) {
        List<Long> jobIds = resumes.stream().map(Resume::getJobId).filter(Objects::nonNull).distinct().toList();
        if (jobIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Job> jobsById = new HashMap<>();
        jobs.findAllById(jobIds).forEach(job -> jobsById.put(job.getId(), job));
        List<Long> companyIds = jobsById.values().stream().map(Job::getCompanyId).filter(Objects::nonNull)
                .distinct().toList();
        Map<Long, Company> companiesById = new HashMap<>();
        if (!companyIds.isEmpty()) {
            companies.findAllById(companyIds).forEach(company -> companiesById.put(company.getId(), company));
        }
        Map<Long, ResumeJobLabel> labels = new HashMap<>();
        for (Resume resume : resumes) {
            Job job = resume.getJobId() == null ? null : jobsById.get(resume.getJobId());
            if (job == null) {
                continue;
            }
            Company company = job.getCompanyId() == null ? null : companiesById.get(job.getCompanyId());
            labels.put(resume.getId(), new ResumeJobLabel(job.getTitle(), company == null ? null : company.getName()));
        }
        return labels;
    }

    public ResumeJobLabel forResume(Resume resume) {
        return forResumes(List.of(resume)).get(resume.getId());
    }
}
