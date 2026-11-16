package com.careercopilot.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Off unless MAIL_SYNC_ENABLED=true, so local runs and tests never contact a mailbox. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.mail.sync-enabled", havingValue = "true")
public class MailSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(MailSyncScheduler.class);

    private final MailSyncService syncService;

    public MailSyncScheduler(MailSyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(cron = "${app.mail.sync-cron:0 0 * * * *}")
    public void run() {
        try {
            log.info("Mailbox scan finished, {} new replies", syncService.syncAll());
        } catch (RuntimeException e) {
            log.error("Mailbox scan failed ({})", e.getClass().getSimpleName());
        }
    }
}
