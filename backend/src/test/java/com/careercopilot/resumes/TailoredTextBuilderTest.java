package com.careercopilot.resumes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TailoredTextBuilderTest {

    @Test
    void putsTheHeadlineThenTheSkillsLineThenABlankLineThenTheOriginalUnchanged() {
        String original = "Jane Doe\n  Java developer\n\nWorked at Acme.";

        String text = TailoredTextBuilder.build("Backend Engineer", List.of("Java", "Docker"), original);

        assertThat(text).isEqualTo("Backend Engineer\nRelevant skills: Java, Docker\n\n" + original);
    }

    @Test
    void leavesOutTheSkillsLineWhenThereAreNoRelevantSkills() {
        String text = TailoredTextBuilder.build("Backend Engineer", List.of(), "Original");

        assertThat(text).isEqualTo("Backend Engineer\n\nOriginal");
        assertThat(text).doesNotContain("Relevant skills");
    }

    @Test
    void leavesOutTheHeadlineWhenThereIsNone() {
        assertThat(TailoredTextBuilder.build(null, List.of("Java"), "Original"))
                .isEqualTo("Relevant skills: Java\n\nOriginal");
        assertThat(TailoredTextBuilder.build("  ", List.of("Java"), "Original"))
                .isEqualTo("Relevant skills: Java\n\nOriginal");
    }

    @Test
    void returnsTheOriginalAloneWhenThereIsNothingToAdd() {
        assertThat(TailoredTextBuilder.build(null, List.of(), "Original")).isEqualTo("Original");
        assertThat(TailoredTextBuilder.build(null, null, null)).isEmpty();
    }

    @Test
    void onlyAddsTheSkillsItWasGiven() {
        String text = TailoredTextBuilder.build("Dev", List.of("Java"), "I know Java.");

        assertThat(text).doesNotContain("Docker").doesNotContain("Kubernetes");
    }
}
