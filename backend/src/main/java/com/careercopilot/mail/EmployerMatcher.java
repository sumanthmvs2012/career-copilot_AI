package com.careercopilot.mail;

import com.careercopilot.mail.MailProvider.MailMessage;
import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Decides which application an incoming message belongs to, using only the sender, the subject and the
 * company name or website domain. Pure functions, no I/O.
 */
final class EmployerMatcher {

    /** One application the scan can attach replies to. */
    record Candidate(Long applicationId, Instant submittedAt, String companyName, String website) {
    }

    private static final Pattern LEGAL_SUFFIX = Pattern.compile("\\b(inc|llc|ltd|corp|corporation|co|gmbh|plc)\\b\\.?");

    private EmployerMatcher() {
    }

    /** The newest application submitted before the message arrived whose company appears in it. */
    static Optional<Long> match(MailMessage message, List<Candidate> candidates) {
        return candidates.stream()
                .filter(c -> c.submittedAt() != null && message.receivedAt().isAfter(c.submittedAt()))
                .filter(c -> mentionsCompany(message, c))
                .max(Comparator.comparing(Candidate::submittedAt).thenComparing(Candidate::applicationId))
                .map(Candidate::applicationId);
    }

    static boolean mentionsCompany(MailMessage message, Candidate candidate) {
        String from = lower(message.from());
        String subject = lower(message.subject());
        String domain = senderDomain(from);

        String name = lower(candidate.companyName()).strip();
        if (name.length() >= 3 && containsWord(from + " " + subject, name)) {
            return true;
        }
        String compact = LEGAL_SUFFIX.matcher(name).replaceAll("").replaceAll("[^a-z0-9]", "");
        if (compact.length() >= 3 && List.of(domain.split("\\.")).contains(compact)) {
            return true;
        }
        String host = websiteHost(candidate.website());
        return !host.isEmpty() && (domain.equals(host) || domain.endsWith("." + host) || subject.contains(host));
    }

    private static boolean containsWord(String text, String word) {
        return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(word) + "(?![a-z0-9])").matcher(text).find();
    }

    private static String senderDomain(String from) {
        int at = from.lastIndexOf('@');
        if (at < 0) {
            return "";
        }
        int end = at + 1;
        while (end < from.length() && (Character.isLetterOrDigit(from.charAt(end)) || from.charAt(end) == '.'
                || from.charAt(end) == '-')) {
            end++;
        }
        return from.substring(at + 1, end);
    }

    private static String websiteHost(String website) {
        if (website == null || website.isBlank()) {
            return "";
        }
        try {
            String value = website.strip();
            URI uri = URI.create(value.contains("://") ? value : "https://" + value);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
