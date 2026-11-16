package com.careercopilot.mail;

import com.careercopilot.applications.Application;
import com.careercopilot.applications.ApplicationRepository;
import com.careercopilot.jobs.Company;
import com.careercopilot.jobs.CompanyRepository;
import com.careercopilot.jobs.Job;
import com.careercopilot.jobs.JobRepository;
import com.careercopilot.mail.MailProvider.MailMessage;
import com.careercopilot.profiles.ProfileRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Scans a mailbox for employer replies. From each message only the id, time, subject and classification are
 * kept; the sender and the snippet are used in memory and dropped. Nothing from a message is ever logged.
 */
@Service
public class MailSyncService {

    static final int SUBJECT_MAX = 300;
    static final int MESSAGE_ID_MAX = 200;

    private static final Logger log = LoggerFactory.getLogger(MailSyncService.class);

    public record SyncResult(int checked, int newReplies) {
    }

    private final MailProvider provider;
    private final TokenCipher cipher;
    private final EmailClassifier classifier;
    private final MailConnectionRepository connections;
    private final ProfileRepository profiles;
    private final ApplicationRepository applications;
    private final JobRepository jobs;
    private final CompanyRepository companies;
    private final EmailLogRepository emailLogs;
    private final Clock clock;

    public MailSyncService(MailProvider provider, TokenCipher cipher, EmailClassifier classifier,
            MailConnectionRepository connections, ProfileRepository profiles, ApplicationRepository applications,
            JobRepository jobs, CompanyRepository companies, EmailLogRepository emailLogs, Clock clock) {
        this.provider = provider;
        this.cipher = cipher;
        this.classifier = classifier;
        this.connections = connections;
        this.profiles = profiles;
        this.applications = applications;
        this.jobs = jobs;
        this.companies = companies;
        this.emailLogs = emailLogs;
        this.clock = clock;
    }

    /** Scans one connection. Not transactional: the mailbox call must not hold a database connection. */
    public SyncResult syncConnection(MailConnection connection) {
        List<EmployerMatcher.Candidate> candidates = candidatesOf(connection.getUserId());
        int checked = 0;
        int created = 0;
        if (!candidates.isEmpty()) {
            Instant since = candidates.stream().map(EmployerMatcher.Candidate::submittedAt)
                    .min(Comparator.naturalOrder()).orElseThrow();
            String refreshToken = cipher.decrypt(connection.getEncryptedRefreshToken());
            List<MailMessage> messages = provider.listMessagesSince(refreshToken, since);
            checked = messages.size();
            for (MailMessage message : messages) {
                if (store(message, candidates)) {
                    created++;
                }
            }
        }
        connection.setLastSyncedAt(clock.instant());
        connections.save(connection);
        log.info("Mailbox scan for connection {}: {} checked, {} new replies", connection.getId(), checked, created);
        return new SyncResult(checked, created);
    }

    /** The hourly job: every active connection, one failing mailbox never stops the others. */
    public int syncAll() {
        int newReplies = 0;
        for (MailConnection connection : connections.findByRevokedAtIsNull()) {
            if (connection.getEncryptedRefreshToken() == null) {
                continue;
            }
            try {
                newReplies += syncConnection(connection).newReplies();
            } catch (RuntimeException e) {
                // Only the class name: exception text from a provider could contain request details.
                log.warn("Mailbox scan failed for connection {} ({})", connection.getId(),
                        e.getClass().getSimpleName());
            }
        }
        return newReplies;
    }

    private boolean store(MailMessage message, List<EmployerMatcher.Candidate> candidates) {
        if (message.id() == null || message.receivedAt() == null) {
            return false;
        }
        Long applicationId = EmployerMatcher.match(message, candidates).orElse(null);
        String externalId = truncate(message.id(), MESSAGE_ID_MAX);
        if (applicationId == null || emailLogs.existsByApplicationIdAndExternalMessageId(applicationId, externalId)) {
            return false;
        }
        EmailLog reply = new EmailLog(applicationId, EmailLog.TYPE_REPLY, message.receivedAt(),
                EmailLog.STATUS_RECEIVED);
        reply.setClassification(classifier.classify(message.subject(), message.snippet()));
        reply.setSubject(truncate(message.subject(), SUBJECT_MAX));
        reply.setExternalMessageId(externalId);
        try {
            emailLogs.saveAndFlush(reply);
            return true;
        } catch (DataIntegrityViolationException e) {
            // A parallel scan stored the same message first.
            return false;
        }
    }

    private List<EmployerMatcher.Candidate> candidatesOf(Long userId) {
        List<Application> sent = profiles.findByUserId(userId)
                .map(profile -> applications.findByProfileIdAndSubmittedAtIsNotNull(profile.getId()))
                .orElse(List.of());
        if (sent.isEmpty()) {
            return List.of();
        }
        Map<Long, Job> jobsById = new HashMap<>();
        jobs.findAllById(sent.stream().map(Application::getJobId).distinct().toList())
                .forEach(job -> jobsById.put(job.getId(), job));
        Map<Long, Company> companiesById = new HashMap<>();
        companies.findAllById(jobsById.values().stream().map(Job::getCompanyId).filter(Objects::nonNull)
                .distinct().toList()).forEach(company -> companiesById.put(company.getId(), company));

        List<EmployerMatcher.Candidate> result = new ArrayList<>();
        for (Application application : sent) {
            Job job = jobsById.get(application.getJobId());
            Company company = job == null || job.getCompanyId() == null ? null : companiesById.get(job.getCompanyId());
            // Without a company there is nothing to recognise a sender by.
            if (company != null) {
                result.add(new EmployerMatcher.Candidate(application.getId(), application.getSubmittedAt(),
                        company.getName(), company.getWebsite()));
            }
        }
        return result;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
