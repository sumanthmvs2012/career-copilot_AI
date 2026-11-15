package com.careercopilot.matches;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.jobs.JobLocation;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocationFitCalculatorTest {

    private static JobLocation place(String city, String state, String country) {
        return new JobLocation(1L, city, state, country);
    }

    @Test
    void aRemoteJobMatchesWhenTheProfileIsOpenToRemote() {
        LocationFit fit = LocationFitCalculator.evaluate(true, "Berlin", "remote", null, List.of());

        assertThat(fit.status()).isEqualTo("match");
        assertThat(fit.note()).contains("remote");
    }

    @Test
    void aRemoteJobWithNoProfileLocationAndNoRemotePreferenceIsUnknown() {
        LocationFit fit = LocationFitCalculator.evaluate(false, null, "remote", null, List.of());

        assertThat(fit.status()).isEqualTo("unknown");
    }

    @Test
    void aRemoteJobWithoutAListedPlaceIsUnknownWhenTheProfileIsNotOpenToRemote() {
        LocationFit fit = LocationFitCalculator.evaluate(false, "Berlin", "remote", null, List.of());

        assertThat(fit.status()).isEqualTo("unknown");
        assertThat(fit.note()).contains("remote");
    }

    @Test
    void aMissingProfileLocationIsUnknown() {
        LocationFit fit = LocationFitCalculator.evaluate(false, "  ", "onsite", "Berlin", List.of());

        assertThat(fit.status()).isEqualTo("unknown");
    }

    @Test
    void aJobWithoutLocationIsUnknown() {
        LocationFit fit = LocationFitCalculator.evaluate(false, "Berlin", "onsite", null, List.of());

        assertThat(fit.status()).isEqualTo("unknown");
    }

    @Test
    void theSameCityIsAMatch() {
        LocationFit fit = LocationFitCalculator.evaluate(false, "Berlin, Germany", "onsite", "Berlin, Germany",
                List.of(place("Berlin", null, "Germany")));

        assertThat(fit.status()).isEqualTo("match");
    }

    @Test
    void theSameCountryButAnotherCityIsPartial() {
        LocationFit fit = LocationFitCalculator.evaluate(false, "Berlin, Germany", "hybrid", "Munich, Germany",
                List.of(place("Munich", null, "Germany")));

        assertThat(fit.status()).isEqualTo("partial");
    }

    @Test
    void theSameStateIsPartial() {
        LocationFit fit = LocationFitCalculator.evaluate(false, "Austin, TX", "onsite", "Dallas, TX",
                List.of(place("Dallas", "TX", "United States")));

        assertThat(fit.status()).isEqualTo("partial");
    }

    @Test
    void aDifferentCountryIsAMismatch() {
        LocationFit fit = LocationFitCalculator.evaluate(false, "Berlin, Germany", "onsite", "Paris, France",
                List.of(place("Paris", null, "France")));

        assertThat(fit.status()).isEqualTo("mismatch");
        assertThat(fit.note()).contains("Paris");
    }

    @Test
    void aRemoteJobInAnotherPlaceMismatchesWhenTheProfileIsNotOpenToRemote() {
        LocationFit fit = LocationFitCalculator.evaluate(false, "Berlin, Germany", "remote", "Remote - Canada",
                List.of(place(null, null, "Canada")));

        assertThat(fit.status()).isEqualTo("mismatch");
        assertThat(fit.note()).contains("not open to remote");
    }

    @Test
    void withoutStructuredPlacesTheFreeTextIsCompared() {
        assertThat(LocationFitCalculator.evaluate(false, "Berlin, Germany", "onsite", "Berlin", List.of()).status())
                .isEqualTo("match");
        assertThat(LocationFitCalculator.evaluate(false, "Berlin, Germany", "onsite", "Munich, Germany", List.of())
                .status()).isEqualTo("partial");
        assertThat(LocationFitCalculator.evaluate(false, "Berlin, Germany", "onsite", "Paris, France", List.of())
                .status()).isEqualTo("mismatch");
    }

    @Test
    void shortOrPartialWordsDoNotMatch() {
        // "Rome" must not match inside "Jerome", and a one letter part is ignored.
        LocationFit fit = LocationFitCalculator.evaluate(false, "Rome", "onsite", "Jerome, AZ",
                List.of(place("Jerome", "AZ", "United States")));

        assertThat(fit.status()).isEqualTo("mismatch");
    }
}
