package com.careercopilot.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Reminders touch only our own database, so they are on by default; tests switch them off. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.mail.reminders-enabled", havingValue = "true", matchIfMissing = true)
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final ReminderService reminders;

    public ReminderScheduler(ReminderService reminders) {
        this.reminders = reminders;
    }

    @Scheduled(cron = "${app.mail.reminder-cron:0 30 * * * *}")
    public void run() {
        try {
            reminders.createDueReminders();
        } catch (RuntimeException e) {
            log.error("Reminder run failed ({})", e.getClass().getSimpleName());
        }
    }
}
