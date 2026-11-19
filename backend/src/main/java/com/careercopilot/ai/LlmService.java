package com.careercopilot.ai;

import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Best-effort access to Claude for the features. Empty means "use the rule-based result": no key, the daily limit
 * reached, a provider error, a refusal or an unreadable reply. Never throws, and never logs prompts or replies.
 */
@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private final AiAvailability availability;
    private final AiUsageLimiter limiter;

    public LlmService(AiAvailability availability, AiUsageLimiter limiter) {
        this.availability = availability;
        this.limiter = limiter;
    }

    public boolean isEnabled() {
        return availability.isLlmEnabled();
    }

    /**
     * @param purpose a short fixed label for logs, for example "interview questions"
     */
    public <T> Optional<T> structured(Long userId, String purpose, String system, String user, Class<T> type) {
        Optional<LlmClient> client = availability.llm();
        if (client.isEmpty()) {
            return Optional.empty();
        }
        try {
            limiter.checkAndRecord(userId);
            return Optional.ofNullable(client.get().completeStructured(system, user, type, 0));
        } catch (RuntimeException e) {
            log.warn("Claude {} unavailable, using the rule-based result: {}", purpose, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * Wraps untrusted text (a scraped job post, a resume) in a tag so the prompt can tell Claude to treat it as data.
     * Any closing tag of the same name inside the text is removed so the text cannot end the block early.
     */
    public static String data(String tag, String text, int maxChars) {
        String value = text == null ? "" : text;
        if (value.length() > maxChars) {
            value = value.substring(0, maxChars);
        }
        String closing = "</" + tag.toLowerCase(Locale.ROOT) + ">";
        StringBuilder cleaned = new StringBuilder(value.length());
        String lower = value.toLowerCase(Locale.ROOT);
        int from = 0;
        for (int at = lower.indexOf(closing); at >= 0; at = lower.indexOf(closing, from)) {
            cleaned.append(value, from, at);
            from = at + closing.length();
        }
        cleaned.append(value.substring(from));
        return "<" + tag + ">\n" + cleaned + "\n" + closing;
    }
}
