package com.careercopilot.mail;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Rule-based: looks for keywords in the subject and Google's snippet. The text is only read here, in memory,
 * and never kept. Rejection is checked first because rejection mails often also say "interview" or "offer"
 * ("we enjoyed interviewing you", "unable to offer you the position").
 */
@Component
public class EmailClassifier {

    public static final String REJECTION = "rejection";
    public static final String INTERVIEW = "interview";
    public static final String OFFER = "offer";
    public static final String OTHER = "other";

    private static final List<String> REJECTION_WORDS = List.of(
            "unfortunately", "regret to inform", "not moving forward", "not be moving forward",
            "will not be proceeding", "not proceed", "decided to pursue other", "other candidates",
            "position has been filled", "unable to offer", "not selected", "no longer being considered",
            "declined your application");
    private static final List<String> OFFER_WORDS = List.of(
            "pleased to offer", "job offer", "offer letter", "offer of employment", "extend an offer",
            "extend you an offer", "excited to offer", "formal offer", "offer to join");
    private static final List<String> INTERVIEW_WORDS = List.of(
            "interview", "phone screen", "schedule a call", "schedule a time", "schedule a chat",
            "technical screen", "video call", "your availability", "meet with our team", "coding challenge",
            "take-home");

    public String classify(String subject, String snippet) {
        String text = ((subject == null ? "" : subject) + " " + (snippet == null ? "" : snippet))
                .toLowerCase(Locale.ROOT);
        if (containsAny(text, REJECTION_WORDS)) {
            return REJECTION;
        }
        if (containsAny(text, OFFER_WORDS)) {
            return OFFER;
        }
        if (containsAny(text, INTERVIEW_WORDS)) {
            return INTERVIEW;
        }
        return OTHER;
    }

    private static boolean containsAny(String text, List<String> words) {
        return words.stream().anyMatch(text::contains);
    }
}
