package com.careercopilot.jobs.scraper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

class GreenhouseJobSourceTest {

    private static final String JSON = """
            {"jobs":[
              {"id":4012345,"title":"Senior Backend Engineer","company_name":"Stripe",
               "first_published":"2026-11-03T15:04:05-05:00",
               "location":{"name":"Remote - US"},
               "departments":[{"name":" "},{"name":"Engineering"},{"name":"Other"}],
               "absolute_url":"https://boards.greenhouse.io/stripe/jobs/4012345",
               "content":"&lt;p&gt;Build &amp;amp; run payments.&lt;/p&gt;&lt;ul&gt;&lt;li&gt;Java&lt;/li&gt;&lt;li&gt;SQL&lt;/li&gt;&lt;/ul&gt;"},
              {"id":4012346,"title":"Designer","location":null,"content":null},
              {"title":"No id so it is skipped"},
              {"id":4012347,"location":{"name":"Berlin"}}
            ]}
            """;

    @Test
    void parsesPostingsAndUnescapesHtmlToPlainText() {
        List<ScrapedJob> jobs = new GreenhouseJobSource(RestClient.builder()).parse(JSON);

        assertThat(jobs).hasSize(2);
        ScrapedJob first = jobs.get(0);
        assertThat(first.externalId()).isEqualTo("4012345");
        assertThat(first.title()).isEqualTo("Senior Backend Engineer");
        assertThat(first.companyName()).isEqualTo("Stripe");
        assertThat(first.location()).isEqualTo("Remote - US");
        assertThat(first.remoteType()).isEqualTo("remote");
        assertThat(first.sourceUrl()).isEqualTo("https://boards.greenhouse.io/stripe/jobs/4012345");
        assertThat(first.postedAt()).isEqualTo(Instant.parse("2026-11-03T20:04:05Z"));
        assertThat(first.department()).isEqualTo("Engineering");
        assertThat(first.description()).isEqualTo("Build & run payments.\n\n- Java\n- SQL");
        assertThat(first.description()).doesNotContain("<").doesNotContain("&lt;");
    }

    @Test
    void missingFieldsBecomeNullAndNeverGuessARemoteType() {
        ScrapedJob second = new GreenhouseJobSource(RestClient.builder()).parse(JSON).get(1);

        assertThat(second.title()).isEqualTo("Designer");
        assertThat(second.location()).isNull();
        assertThat(second.remoteType()).isNull();
        assertThat(second.postedAt()).isNull();
        assertThat(second.department()).isNull();
        assertThat(second.sourceUrl()).isNull();
        assertThat(second.description()).isEmpty();
    }

    @Test
    void emptyOrMissingBodyGivesNoJobs() {
        GreenhouseJobSource source = new GreenhouseJobSource(RestClient.builder());
        assertThat(source.parse("")).isEmpty();
        assertThat(source.parse("{}")).isEmpty();
    }

    @Test
    void fetchCallsTheBoardUrlOnceWithTheScraperUserAgent() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(GreenhouseJobSource.BASE_URL)
                .defaultHeader("User-Agent", ScraperHttp.USER_AGENT);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://boards-api.greenhouse.io/v1/boards/stripe/jobs?content=true"))
                .andExpect(header("User-Agent", ScraperHttp.USER_AGENT))
                .andRespond(withSuccess(JSON, MediaType.APPLICATION_JSON));

        List<ScrapedJob> jobs = new GreenhouseJobSource(builder).fetch("stripe");

        assertThat(jobs).hasSize(2);
        server.verify();
    }

    @Test
    void serverErrorSurfacesAsAnHttpStatusException() {
        RestClient.Builder builder = RestClient.builder().baseUrl(GreenhouseJobSource.BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://boards-api.greenhouse.io/v1/boards/gone/jobs?content=true"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> new GreenhouseJobSource(builder).fetch("gone"))
                .isInstanceOf(RestClientResponseException.class);
    }

    @Test
    void sourceNameMatchesTheAtsType() {
        assertThat(new GreenhouseJobSource(RestClient.builder()).source()).isEqualTo("greenhouse");
    }
}
