package com.careercopilot.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JobFilterTest {

    @Test
    void noValuesGivesNoFiltersAndOffsetZero() {
        assertThat(JobFilter.parse(null, null, null, null, null, null)).isEqualTo(JobFilter.NONE);
    }

    @Test
    void validValuesAreTrimmedAndNormalised() {
        JobFilter filter = JobFilter.parse(" Dev ", " LEVER ", "Remote", " Berlin ", " 7 ", " 20 ");

        assertThat(filter).isEqualTo(new JobFilter("Dev", "lever", "remote", "Berlin", 7, 20));
    }

    @Test
    void textOfExactlyOneHundredCharactersIsAccepted() {
        assertThat(JobFilter.parse("a".repeat(100), null, null, "b".repeat(100), null, null).q()).hasSize(100);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankQOrLocationIsRejected(String value) {
        assertThatThrownBy(() -> JobFilter.parse(value, null, null, null, null, null))
                .isInstanceOf(InvalidJobQueryException.class);
        assertThatThrownBy(() -> JobFilter.parse(null, null, null, value, null, null))
                .isInstanceOf(InvalidJobQueryException.class);
    }

    @Test
    void tooLongQOrLocationIsRejected() {
        String tooLong = "a".repeat(101);
        assertThatThrownBy(() -> JobFilter.parse(tooLong, null, null, null, null, null))
                .isInstanceOf(InvalidJobQueryException.class);
        assertThatThrownBy(() -> JobFilter.parse(null, null, null, tooLong, null, null))
                .isInstanceOf(InvalidJobQueryException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "indeed", "linkedin", "all"})
    void unknownSourceIsRejected(String value) {
        assertThatThrownBy(() -> JobFilter.parse(null, value, null, null, null, null))
                .isInstanceOf(InvalidJobQueryException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"greenhouse", "lever", "ashby", "workable", "smartrecruiters", "manual"})
    void everyDocumentedSourceIsAccepted(String value) {
        assertThat(JobFilter.parse(null, value, null, null, null, null).source()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "office", "on-site", "any"})
    void unknownRemoteTypeIsRejected(String value) {
        assertThatThrownBy(() -> JobFilter.parse(null, null, value, null, null, null))
                .isInstanceOf(InvalidJobQueryException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "2", "14", "31", "-7", "abc", "7.5"})
    void unsupportedPostedWithinIsRejected(String value) {
        assertThatThrownBy(() -> JobFilter.parse(null, null, null, null, value, null))
                .isInstanceOf(InvalidJobQueryException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "abc", "1.5", "99999999999"})
    void badOffsetIsRejected(String value) {
        assertThatThrownBy(() -> JobFilter.parse(null, null, null, null, null, value))
                .isInstanceOf(InvalidJobQueryException.class);
    }

    @Test
    void blankOffsetMeansZero() {
        assertThat(JobFilter.parse(null, null, null, null, null, " ").offset()).isZero();
    }

    @Test
    void likeWildcardsAndTheEscapeCharacterAreEscaped() {
        assertThat(JobSearchRepository.escapeLike("100%_a\\b")).isEqualTo("100\\%\\_a\\\\b");
    }

    @Test
    void categoryIsMatchedIgnoringCaseAndReturnedInCanonicalForm() {
        JobFilter filter = JobFilter.parse(null, null, null, null, null, null, " data & ml ", null, null, null);

        assertThat(filter.category()).isEqualTo("Data & ML");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "Astronaut", "Software"})
    void unknownOrEmptyCategoryIsRejected(String value) {
        assertThatThrownBy(() -> JobFilter.parse(null, null, null, null, null, null, value, null, null, null))
                .isInstanceOf(InvalidJobQueryException.class);
    }

    @Test
    void placeValuesAreTrimmedAndLimitedTo120Characters() {
        JobFilter filter = JobFilter.parse(null, null, null, null, null, null, null, " Germany ", " Bavaria", "Munich ");

        assertThat(filter.country()).isEqualTo("Germany");
        assertThat(filter.state()).isEqualTo("Bavaria");
        assertThat(filter.city()).isEqualTo("Munich");
        assertThat(JobFilter.place("a".repeat(120), "city")).hasSize(120);
        assertThat(JobFilter.place(null, "city")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void emptyPlaceValuesAreRejected(String value) {
        assertThatThrownBy(() -> JobFilter.parse(null, null, null, null, null, null, null, value, null, null))
                .isInstanceOf(InvalidJobQueryException.class);
        assertThatThrownBy(() -> JobFilter.parse(null, null, null, null, null, null, null, null, value, null))
                .isInstanceOf(InvalidJobQueryException.class);
        assertThatThrownBy(() -> JobFilter.parse(null, null, null, null, null, null, null, null, null, value))
                .isInstanceOf(InvalidJobQueryException.class);
    }

    @Test
    void overlongPlaceValuesAreRejected() {
        assertThatThrownBy(() -> JobFilter.place("a".repeat(121), "state"))
                .isInstanceOf(InvalidJobQueryException.class);
    }
}
