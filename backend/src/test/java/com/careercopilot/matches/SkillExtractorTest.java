package com.careercopilot.matches;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkillExtractorTest {

    private final SkillExtractor extractor = new SkillExtractor();

    @Test
    void blankOrNullTextHasNoSkills() {
        assertThat(extractor.extract(null)).isEmpty();
        assertThat(extractor.extract("   ")).isEmpty();
    }

    @Test
    void findsSkillsCaseInsensitivelyInOrderOfFirstAppearanceWithoutDuplicates() {
        assertThat(extractor.extract("PYTHON, docker, python again and Docker")).containsExactly("Python", "Docker");
    }

    @Test
    void javaIsNotFoundInsideJavaScript() {
        assertThat(extractor.extract("We use JavaScript every day")).containsExactly("JavaScript");
        assertThat(extractor.extract("Java and JavaScript")).containsExactly("Java", "JavaScript");
    }

    @Test
    void handlesSymbolsInSkillNames() {
        assertThat(extractor.extract("C++, C#, .NET and Node.js")).containsExactly("C++", "C#", ".NET", "Node.js");
    }

    @Test
    void aSkillEndingASentenceStillCounts() {
        assertThat(extractor.extract("Strong in Java. Also Node.js.")).containsExactly("Java", "Node.js");
    }

    @Test
    void codeLikeTextDoesNotCountAsTheSkill() {
        assertThat(extractor.extract("import java.util.List")).isEmpty();
    }

    @Test
    void aliasesMapToTheCanonicalName() {
        assertThat(extractor.extract("ReactJS, NodeJS, Postgres and k8s"))
                .containsExactly("React", "Node.js", "PostgreSQL", "Kubernetes");
    }

    @Test
    void reactNativeIsNotAlsoReact() {
        assertThat(extractor.extract("Built apps with React Native")).containsExactly("React Native");
        assertThat(extractor.extract("React Native and React")).containsExactly("React Native", "React");
    }

    @Test
    void springBootAndAspNetKeepTheirOwnNames() {
        assertThat(extractor.extract("Spring Boot services")).containsExactly("Spring Boot");
        assertThat(extractor.extract("ASP.NET Core API")).containsExactly(".NET");
    }

    @Test
    void goIsOnlyFoundWhenWrittenAsGolangOrWithClearContext() {
        assertThat(extractor.extract("We are ready to go and need someone who can go fast")).isEmpty();
        assertThat(extractor.extract("Backend in Golang")).containsExactly("Go");
        assertThat(extractor.extract("Experience with Go language")).containsExactly("Go");
    }

    @Test
    void rAndCNeedClearContextToo() {
        assertThat(extractor.extract("Plan A or plan R, vitamin C")).isEmpty();
        assertThat(extractor.extract("Statistics in R programming and C language")).contains("R", "C");
    }

    @Test
    void wordsInsideOtherWordsAreNotSkills() {
        assertThat(extractor.extract("Gitlab is not Git, and MySQL is not SQL")).contains("GitLab", "Git", "MySQL");
        assertThat(extractor.extract("MySQL only")).containsExactly("MySQL");
        assertThat(extractor.extract("Reactive programming")).isEmpty();
    }

    @Test
    void matchesAcrossLineBreaks() {
        assertThat(extractor.extract("Spring\nBoot")).containsExactly("Spring Boot");
    }

    @Test
    void relatedSkillsAreSymmetric() {
        assertThat(extractor.relatedTo("React")).contains("React Native");
        assertThat(extractor.relatedTo("React Native")).contains("React");
        assertThat(extractor.relatedTo("Unknown skill")).isEmpty();
    }

    @Test
    void skillWordsAreRecognisedForKeywordFiltering() {
        assertThat(extractor.isSkillWord("react")).isTrue();
        assertThat(extractor.isSkillWord("kubernetes")).isTrue();
        assertThat(extractor.isSkillWord("payment")).isFalse();
        assertThat(extractor.isSkillWord("negotiation")).isFalse();
    }

    @Test
    void theBundledDictionaryKnowsCommonSkills() {
        List<String> found = extractor.extract(
                "Java JavaScript TypeScript Python Docker Kubernetes AWS Azure Terraform Kafka Redis PostgreSQL");
        assertThat(found).hasSize(12);
    }

    @Test
    void customDictionaryEntriesWithMissingListsWork() {
        SkillExtractor small = new SkillExtractor(List.of(new SkillExtractor.Entry("Foo", null, null)));
        assertThat(small.extract("we like foo")).containsExactly("Foo");
        assertThat(small.relatedTo("Foo")).isEmpty();
    }

    @Test
    void companySkillsDropsASkillNamedLikeTheCompany() {
        assertThat(CompanySkills.exclude(List.of("Stripe", "Java"), "stripe")).containsExactly("Java");
        assertThat(CompanySkills.exclude(List.of("Stripe", "Java"), "Stripe, Inc")).containsExactly("Stripe", "Java");
        assertThat(CompanySkills.exclude(List.of("Java"), null)).containsExactly("Java");
        assertThat(CompanySkills.exclude(List.of("Java"), " ")).containsExactly("Java");
    }
}
