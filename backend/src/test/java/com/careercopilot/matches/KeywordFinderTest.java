package com.careercopilot.matches;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class KeywordFinderTest {

    private static List<String> find(String job, String resume) {
        return KeywordFinder.missing(job, resume, word -> word.equals("java"), 10);
    }

    @Test
    void blankJobTextHasNoKeywords() {
        assertThat(find(null, "x")).isEmpty();
        assertThat(find("  ", "x")).isEmpty();
    }

    @Test
    void ranksByFrequencyThenFirstAppearance() {
        String job = "billing billing billing invoices invoices ledger ledger";

        assertThat(find(job, null)).containsExactly("billing", "invoices", "ledger");
    }

    @Test
    void wordsUsedOnlyOnceAreNotFrequent() {
        assertThat(find("billing invoices ledger", "")).isEmpty();
    }

    @Test
    void skipsStopWordsSkillWordsAndWordsOnTheResume() {
        String job = "experience experience java java billing billing ledger ledger the the";

        assertThat(find(job, "I worked on a Ledger")).containsExactly("billing");
    }

    @Test
    void pluralsFoldOntoTheResumeSingular() {
        assertThat(find("invoices invoices", "invoice")).isEmpty();
    }

    @Test
    void honoursTheLimit() {
        assertThat(KeywordFinder.missing("alpha alpha beta beta gamma gamma", "", w -> false, 2))
                .containsExactly("alpha", "beta");
    }
}
