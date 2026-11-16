package com.careercopilot.interviews;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.interviews.InterviewQuestionGenerator.Generated;
import com.careercopilot.interviews.InterviewQuestionGenerator.Input;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class InterviewQuestionGeneratorTest {

    private static final Set<String> CATEGORIES = Set.of("technical", "behavioral", "role", "company", "resume_gap");

    private final InterviewQuestionGenerator generator = new InterviewQuestionGenerator();

    private static void assertValid(List<Generated> questions) {
        assertThat(questions).hasSizeBetween(20, 30);
        assertThat(questions).extracting(Generated::category).allMatch(CATEGORIES::contains);
        assertThat(questions).extracting(g -> g.question().toLowerCase(Locale.ROOT)).doesNotHaveDuplicates();
        assertThat(questions).extracting(Generated::question).allMatch(q -> !q.isBlank() && q.length() <= 1000);
    }

    private static Set<String> categories(List<Generated> questions) {
        return questions.stream().map(Generated::category).collect(Collectors.toSet());
    }

    @Test
    void aRichJobWithAResumeGivesBetween20And30UniqueQuestionsInEveryCategory() {
        List<Generated> questions = generator.generate(new Input("Backend Engineer", "Stripe",
                List.of("Java", "Spring Boot", "PostgreSQL", "Docker", "Kubernetes", "AWS", "Kafka", "Redis",
                        "GraphQL", "Terraform"),
                List.of("Java", "PostgreSQL", "Python")));

        assertValid(questions);
        assertThat(categories(questions)).isEqualTo(CATEGORIES);
        assertThat(questions).extracting(Generated::question)
                .anyMatch(q -> q.contains("Backend Engineer")).anyMatch(q -> q.contains("Stripe"))
                .anyMatch(q -> q.contains("Kubernetes"));
    }

    @Test
    void resumeGapQuestionsNameSkillsTheResumeLacksAndStrengthsItHas() {
        List<Generated> gaps = generator.generate(new Input("Engineer", "Acme", List.of("Java", "Docker", "Kafka"),
                List.of("Java"))).stream().filter(g -> g.category().equals("resume_gap")).toList();

        assertThat(gaps).hasSize(3);
        assertThat(gaps.get(0).question()).contains("Java").contains("resume lists");
        assertThat(gaps.get(1).question()).contains("Docker").contains("not see");
        assertThat(gaps.get(2).question()).contains("Kafka");
    }

    @Test
    void withoutAResumeThereAreNoResumeGapQuestions() {
        List<Generated> questions = generator.generate(new Input("Engineer", "Acme", List.of("Java", "Docker"),
                null));

        assertValid(questions);
        assertThat(categories(questions)).doesNotContain("resume_gap");
    }

    @Test
    void aJobWithNoSkillsTitleOrCompanyStillGetsAFullList() {
        List<Generated> questions = generator.generate(new Input(null, null, List.of(), List.of()));

        assertValid(questions);
        assertThat(questions).extracting(Generated::question).anyMatch(q -> q.contains("this role"))
                .anyMatch(q -> q.contains("this company")).noneMatch(q -> q.contains("null"));
        assertThat(generator.generate(new Input("  ", " ", null, null))).hasSizeBetween(20, 30);
    }

    @Test
    void aJobWithOneSkillIsToppedUpToTheMinimum() {
        assertValid(generator.generate(new Input("Analyst", "Acme", List.of("SQL"), List.of())));
    }

    @Test
    void sizeStaysWithinTheLimitsForEverySkillCount() {
        List<String> all = List.of("A1", "B2", "C3", "D4", "E5", "F6", "G7", "H8", "I9", "J10", "K11", "L12");
        for (int skills = 0; skills <= all.size(); skills++) {
            List<String> job = all.subList(0, skills);
            assertValid(generator.generate(new Input("Dev", "Co", job, List.of())));
            assertValid(generator.generate(new Input("Dev", "Co", job, null)));
            assertValid(generator.generate(new Input("Dev", "Co", job, job)));
        }
    }

    @Test
    void sameInputGivesTheSameListInTheSameOrder() {
        Input input = new Input("Data Engineer", "Globex", List.of("Python", "Spark", "SQL"), List.of("SQL"));

        assertThat(generator.generate(input)).isEqualTo(generator.generate(input));
        assertThat(new InterviewQuestionGenerator().generate(input)).isEqualTo(generator.generate(input));
    }

    @Test
    void differentJobsGiveDifferentQuestions() {
        List<Generated> a = generator.generate(new Input("Backend Engineer", "Stripe", List.of("Java"), null));
        List<Generated> b = generator.generate(new Input("Designer", "Figma", List.of("Figma"), null));

        assertThat(a).isNotEqualTo(b);
    }
}
