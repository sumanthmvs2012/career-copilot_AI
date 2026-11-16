package com.careercopilot.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.mail.EmployerMatcher.Candidate;
import com.careercopilot.mail.MailProvider.MailMessage;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmployerMatcherTest {

    private static final Instant SUBMITTED = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant RECEIVED = Instant.parse("2026-11-05T10:00:00Z");

    private static MailMessage message(String from, String subject, Instant at) {
        return new MailMessage("m1", from, subject, at, "snippet text");
    }

    private static Candidate candidate(long id, String name, String website, Instant submitted) {
        return new Candidate(id, submitted, name, website);
    }

    @Test
    void matchesByCompanyNameInTheSenderOrSubject() {
        List<Candidate> candidates = List.of(candidate(1, "Stripe", null, SUBMITTED));

        assertThat(EmployerMatcher.match(message("Stripe Recruiting <jobs@mail.example>", "Hello", RECEIVED),
                candidates)).contains(1L);
        assertThat(EmployerMatcher.match(message("hr@mail.example", "Your Stripe application", RECEIVED),
                candidates)).contains(1L);
    }

    @Test
    void matchesBySenderDomainLabelOrWebsiteDomain() {
        assertThat(EmployerMatcher.match(message("no-reply@stripe.com", "Hello", RECEIVED),
                List.of(candidate(1, "Stripe, Inc.", null, SUBMITTED)))).contains(1L);
        assertThat(EmployerMatcher.match(message("jobs@careers.acme-corp.io", "Hello", RECEIVED),
                List.of(candidate(2, "Totally Different", "https://www.acme-corp.io/about", SUBMITTED)))).contains(2L);
    }

    @Test
    void ignoresMessagesThatDoNotMentionTheCompanyOrWordsInsideOtherWords() {
        List<Candidate> candidates = List.of(candidate(1, "Meta", null, SUBMITTED));

        assertThat(EmployerMatcher.match(message("x@y.com", "Your metadata export", RECEIVED), candidates)).isEmpty();
        assertThat(EmployerMatcher.match(message("x@y.com", "Hello", RECEIVED), candidates)).isEmpty();
    }

    @Test
    void ignoresMessagesReceivedBeforeTheApplicationWasSubmitted() {
        List<Candidate> candidates = List.of(candidate(1, "Stripe", null, SUBMITTED));

        assertThat(EmployerMatcher.match(message("jobs@stripe.com", "Hi", Instant.parse("2026-10-30T00:00:00Z")),
                candidates)).isEmpty();
    }

    @Test
    void picksTheMostRecentlySubmittedApplicationOfTheSameCompany() {
        List<Candidate> candidates = List.of(
                candidate(1, "Stripe", null, SUBMITTED),
                candidate(2, "Stripe", null, Instant.parse("2026-11-03T10:00:00Z")),
                candidate(3, "Stripe", null, Instant.parse("2026-11-20T10:00:00Z")));

        assertThat(EmployerMatcher.match(message("jobs@stripe.com", "Hi", RECEIVED), candidates)).contains(2L);
    }

    @Test
    void tooShortNamesAreNotMatchedByWord() {
        assertThat(EmployerMatcher.match(message("x@y.com", "Hi to AB team", RECEIVED),
                List.of(candidate(1, "AB", null, SUBMITTED)))).isEmpty();
    }

    @Test
    void toStringOfMessagesNeverShowsTheSnippet() {
        assertThat(message("a@b.c", "s", RECEIVED).toString()).doesNotContain("snippet text");
    }
}
