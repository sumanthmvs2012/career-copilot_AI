package com.careercopilot.jobs.scraper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class LeverJobSourceTest {

    private static final String JSON = """
            [
              {"id":"0a1b2c3d-1111-2222-3333-444455556666","text":"Staff Engineer",
               "categories":{"location":"London","team":"Platform"},
               "hostedUrl":"https://jobs.lever.co/palantir/0a1b2c3d",
               "createdAt":1762300800000,
               "workplaceType":"hybrid",
               "descriptionPlain":"Work on data platforms.\\n\\n\\n\\nWith great people.",
               "lists":[{"text":"Requirements","content":"<li>Java</li><li>Kafka &amp; Spark</li>"}],
               "additionalPlain":"We are an equal opportunity employer."},
              {"id":"ffff0000-1111-2222-3333-444455556666","text":"Recruiter",
               "categories":{"location":"Remote (EU)","department":"People"},
               "description":"<div>Hire <b>great</b> people.<br>Fast.</div>"},
              {"id":"aaaa0000-1111-2222-3333-444455556666","text":"Analyst","categories":{}},
              {"text":"No id"}
            ]
            """;

    @Test
    void parsesPlainDescriptionListsAndEpochMillis() {
        List<ScrapedJob> jobs = new LeverJobSource(RestClient.builder()).parse(JSON);

        assertThat(jobs).hasSize(3);
        ScrapedJob first = jobs.get(0);
        assertThat(first.externalId()).isEqualTo("0a1b2c3d-1111-2222-3333-444455556666");
        assertThat(first.title()).isEqualTo("Staff Engineer");
        assertThat(first.companyName()).isNull();
        assertThat(first.department()).isEqualTo("Platform");
        assertThat(first.location()).isEqualTo("London");
        assertThat(first.remoteType()).isEqualTo("hybrid");
        assertThat(first.sourceUrl()).isEqualTo("https://jobs.lever.co/palantir/0a1b2c3d");
        assertThat(first.postedAt()).isEqualTo(Instant.ofEpochMilli(1762300800000L));
        assertThat(first.description()).isEqualTo(
                "Work on data platforms.\n\nWith great people.\n\nRequirements\n- Java\n- Kafka & Spark\n\n"
                        + "We are an equal opportunity employer.");
    }

    @Test
    void fallsBackToHtmlDescriptionAndLocationBasedRemoteType() {
        ScrapedJob second = new LeverJobSource(RestClient.builder()).parse(JSON).get(1);

        assertThat(second.description()).isEqualTo("Hire great people.\nFast.");
        assertThat(second.remoteType()).isEqualTo("remote");
        assertThat(second.postedAt()).isNull();
        assertThat(second.department()).isEqualTo("People");
    }

    @Test
    void missingFieldsStayNull() {
        ScrapedJob third = new LeverJobSource(RestClient.builder()).parse(JSON).get(2);

        assertThat(third.location()).isNull();
        assertThat(third.department()).isNull();
        assertThat(third.remoteType()).isNull();
        assertThat(third.sourceUrl()).isNull();
        assertThat(third.description()).isEmpty();
    }

    @Test
    void emptyBodyGivesNoJobs() {
        assertThat(new LeverJobSource(RestClient.builder()).parse(" ")).isEmpty();
        assertThat(new LeverJobSource(RestClient.builder()).parse("[]")).isEmpty();
    }

    @Test
    void fetchCallsThePostingsUrl() {
        RestClient.Builder builder = RestClient.builder().baseUrl(LeverJobSource.BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.lever.co/v0/postings/palantir?mode=json"))
                .andRespond(withSuccess(JSON, MediaType.APPLICATION_JSON));

        LeverJobSource source = new LeverJobSource(builder);

        assertThat(source.fetch("palantir")).hasSize(3);
        server.verify();
        assertThat(source.source()).isEqualTo("lever");
    }

    @Test
    void remoteTypeIsReadFromTextOnlyWhenClear() {
        assertThat(ScraperText.remoteTypeFromText("Remote - US")).isEqualTo("remote");
        assertThat(ScraperText.remoteTypeFromText("Hybrid, London")).isEqualTo("hybrid");
        assertThat(ScraperText.remoteTypeFromText("San Francisco")).isNull();
        assertThat(ScraperText.remoteTypeFromText("Remotely located office")).isNull();
        assertThat(ScraperText.remoteTypeFromText(null)).isNull();
    }
}
