package com.careercopilot.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CoverLetterTemplateTest {

    @Test
    void usesTitleCompanyNameAndMatchedSkillsOnly() {
        String letter = CoverLetterTemplate.build("Jane Doe", " Data Analyst ", "Acme", List.of("SQL", "Python"));

        assertThat(letter).contains("the Data Analyst role at Acme.")
                .contains("experience with SQL and Python, which the posting asks for.")
                .endsWith("Kind regards,\nJane Doe");
    }

    @Test
    void capsTheSkillListAndJoinsWithCommas() {
        String letter = CoverLetterTemplate.build("Jane", "Dev", "Acme",
                List.of("A1", "B2", "C3", "D4", "E5", "F6"));

        assertThat(letter).contains("A1, B2, C3, D4 and E5,").doesNotContain("F6");
    }

    @Test
    void omitsTheSkillSentenceAndFallsBackWhenCompanyIsMissing() {
        String letter = CoverLetterTemplate.build("Jane", "Dev", null, List.of());

        assertThat(letter).contains("the Dev role at your team.").doesNotContain("experience with");
    }

    @Test
    void singleSkillHasNoConjunction() {
        assertThat(CoverLetterTemplate.build("Jane", "Dev", "Acme", List.of("Java")))
                .contains("experience with Java, which");
    }
}
