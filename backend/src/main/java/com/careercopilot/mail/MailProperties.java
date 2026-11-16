package com.careercopilot.mail;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Mailbox settings, all optional so the app starts without them (then configured() is false).
 * tokenKey is base64 of 32 random bytes.
 */
@ConfigurationProperties("app.mail")
public record MailProperties(
        @DefaultValue("") String googleClientId,
        @DefaultValue("") String googleClientSecret,
        @DefaultValue("") String googleRedirectUri,
        @DefaultValue("") String tokenKey,
        @DefaultValue("http://localhost:5173") String frontendUrl,
        @DefaultValue("false") boolean syncEnabled,
        @DefaultValue("0 0 * * * *") String syncCron,
        @DefaultValue("true") boolean remindersEnabled,
        @DefaultValue("0 30 * * * *") String reminderCron,
        @DefaultValue("7") int reminderDays,
        @DefaultValue("PT10M") Duration stateTtl,
        @DefaultValue("PT5S") Duration connectTimeout,
        @DefaultValue("PT20S") Duration readTimeout) {

    public boolean configured() {
        return !googleClientId.isBlank() && !googleClientSecret.isBlank() && !googleRedirectUri.isBlank()
                && !tokenKey.isBlank();
    }

    /** Never prints the secrets, in case a properties object ends up in a log line. */
    @Override
    public String toString() {
        return "MailProperties[configured=" + configured() + ", syncEnabled=" + syncEnabled + "]";
    }
}
