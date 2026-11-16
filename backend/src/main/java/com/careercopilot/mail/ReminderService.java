package com.careercopilot.mail;

import com.careercopilot.applications.Application;
import com.careercopilot.applications.ApplicationRepository;
import com.careercopilot.jobs.Company;
import com.careercopilot.jobs.CompanyRepository;
import com.careercopilot.jobs.Job;
import com.careercopilot.jobs.JobRepository;
import com.careercopilot.profiles.Profile;
import com.careercopilot.profiles.ProfileRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** In-app follow-up reminders for applications that got no reply. Nothing is ever sent by email. */
@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    private final ProfileRepository profiles;
    private final ApplicationRepository applications;
    private final JobRepository jobs;
    private final CompanyRepository companies;
    private final EmailLogRepository emailLogs;
    private final MailProperties properties;
    private final Clock clock;

    public ReminderService(ProfileRepository profiles, ApplicationRepository applications, JobRepository jobs,
            CompanyRepository companies, EmailLogRepository emailLogs, MailProperties properties, Clock clock) {
        this.profiles = profiles;
        this.applications = applications;
        this.jobs = jobs;
        this.companies = companies;
        this.emailLogs = emailLogs;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Adds one reminder per applied application that is at least reminderDays old and has no reply. The query
     * skips applications that already have a reminder (even a dismissed one), so none gets a second one.
     */
    @Transactional
    public int createDueReminders() {
        Duration wait = Duration.ofDays(properties.reminderDays());
        List<Application> due = applications.findDueForReminder(clock.instant().minus(wait));
        List<EmailLog> reminders = due.stream()
                // occurred_at is the moment the reminder became due, not the moment the job ran.
                .map(application -> new EmailLog(application.getId(), EmailLog.TYPE_REMINDER,
                        application.getSubmittedAt().plus(wait), EmailLog.STATUS_NEW))
                .toList();
        emailLogs.saveAll(reminders);
        if (!reminders.isEmpty()) {
            log.info("Created {} follow-up reminders", reminders.size());
        }
        return reminders.size();
    }

    @Transactional(readOnly = true)
    public List<ReminderResponse> list(Long userId) {
        Profile profile = profiles.findByUserId(userId).orElse(null);
        if (profile == null) {
            return List.of();
        }
        List<EmailLog> reminders = emailLogs.findNewRemindersOfProfile(profile.getId());
        if (reminders.isEmpty()) {
            return List.of();
        }
        Map<Long, Application> applicationsById = new HashMap<>();
        applications.findAllById(reminders.stream().map(EmailLog::getApplicationId).distinct().toList())
                .forEach(application -> applicationsById.put(application.getId(), application));
        Map<Long, Job> jobsById = new HashMap<>();
        jobs.findAllById(applicationsById.values().stream().map(Application::getJobId).distinct().toList())
                .forEach(job -> jobsById.put(job.getId(), job));
        Map<Long, Company> companiesById = new HashMap<>();
        companies.findAllById(jobsById.values().stream().map(Job::getCompanyId).filter(Objects::nonNull)
                .distinct().toList()).forEach(company -> companiesById.put(company.getId(), company));

        return reminders.stream().map(reminder -> {
            Application application = applicationsById.get(reminder.getApplicationId());
            Job job = application == null ? null : jobsById.get(application.getJobId());
            Company company = job == null || job.getCompanyId() == null ? null : companiesById.get(job.getCompanyId());
            return new ReminderResponse(reminder.getId(), reminder.getApplicationId(),
                    job == null ? null : job.getTitle(), company == null ? null : company.getName(),
                    reminder.getOccurredAt());
        }).toList();
    }

    @Transactional
    public ReminderResponse dismiss(Long userId, Long id) {
        Profile profile = profiles.findByUserId(userId).orElseThrow(ReminderNotFoundException::new);
        EmailLog reminder = emailLogs.findReminderOfProfile(id, profile.getId())
                .orElseThrow(ReminderNotFoundException::new);
        reminder.setStatus(EmailLog.STATUS_DISMISSED);
        emailLogs.save(reminder);
        Application application = applications.findById(reminder.getApplicationId()).orElse(null);
        Job job = application == null ? null : jobs.findById(application.getJobId()).orElse(null);
        Company company = job == null || job.getCompanyId() == null ? null
                : companies.findById(job.getCompanyId()).orElse(null);
        return new ReminderResponse(reminder.getId(), reminder.getApplicationId(),
                job == null ? null : job.getTitle(), company == null ? null : company.getName(),
                reminder.getOccurredAt());
    }
}
