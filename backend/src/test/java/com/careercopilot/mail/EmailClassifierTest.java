package com.careercopilot.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailClassifierTest {

    private final EmailClassifier classifier = new EmailClassifier();

    @Test
    void recognisesRejections() {
        assertThat(classifier.classify("Your application to Acme", "Unfortunately we will not be moving forward"))
                .isEqualTo("rejection");
        assertThat(classifier.classify("Update on your application", "We decided to pursue other candidates."))
                .isEqualTo("rejection");
    }

    @Test
    void rejectionWinsOverWordsLikeInterviewAndOffer() {
        assertThat(classifier.classify("Thanks for interviewing", "We are unable to offer you the position."))
                .isEqualTo("rejection");
        assertThat(classifier.classify("Interview feedback", "Unfortunately it is a no")).isEqualTo("rejection");
    }

    @Test
    void recognisesInterviewInvitations() {
        assertThat(classifier.classify("Interview invitation: Backend Engineer", null)).isEqualTo("interview");
        assertThat(classifier.classify("Next steps", "Could we schedule a call this week?")).isEqualTo("interview");
    }

    @Test
    void recognisesOffers() {
        assertThat(classifier.classify("Your offer letter from Acme", "")).isEqualTo("offer");
        assertThat(classifier.classify("Good news", "We are pleased to offer you the role")).isEqualTo("offer");
    }

    @Test
    void everythingElseIsOtherAndNullsAreSafe() {
        assertThat(classifier.classify("Thanks for applying", "We received your application")).isEqualTo("other");
        assertThat(classifier.classify(null, null)).isEqualTo("other");
    }

    @Test
    void isCaseInsensitive() {
        assertThat(classifier.classify("INTERVIEW REQUEST", null)).isEqualTo("interview");
    }
}
