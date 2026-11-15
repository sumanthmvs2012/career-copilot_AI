package com.careercopilot.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careercopilot.auth.UserRepository;
import com.careercopilot.common.ratelimit.RateLimiter;
import com.careercopilot.profiles.ProfileRepository;
import com.careercopilot.resumes.ResumeRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobFilterIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private ProfileRepository profiles;

    @Autowired
    private ResumeRepository resumes;

    @Autowired
    private JobRepository jobs;

    @Autowired
    private CompanyRepository companies;

    @Autowired
    private RateLimiter rateLimiter;

    private String jane;
    private String john;

    @BeforeEach
    void setUp() throws Exception {
        clean();
        rateLimiter.reset();
        jane = tokenWithProfile("jane@example.com");
        john = tokenWithProfile("john@example.com");
    }

    @AfterEach
    void clean() {
        jobs.deleteAll();
        companies.deleteAll();
        resumes.deleteAll();
        profiles.deleteAll();
        users.deleteAll();
    }

    private String tokenWithProfile(String email) throws Exception {
        String json = mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret-pass\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = "Bearer " + JsonPath.read(json, "$.token");
        mvc.perform(put("/profile").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Some One\"}"))
                .andExpect(status().isOk());
        return token;
    }

    private Company company(String name) {
        return companies.save(new Company(name));
    }

    private Job scraped(String source, String title, Company company, String location, String remoteType,
            Instant postedAt) {
        Job job = new Job(source, title, "Scraped description");
        job.setExternalId(source + "-" + title + "-" + postedAt);
        job.setCompanyId(company == null ? null : company.getId());
        job.setLocation(location);
        job.setRemoteType(remoteType);
        job.setPostedAt(postedAt);
        return jobs.save(job);
    }

    private void paste(String token, String title, String company, String location, String remoteType)
            throws Exception {
        mvc.perform(post("/jobs/match").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"companyName\":\"" + company + "\","
                                + "\"description\":\"Build and run backend services in Java.\","
                                + "\"location\":\"" + location + "\",\"remoteType\":\"" + remoteType + "\"}"))
                .andExpect(status().isCreated());
    }

    private List<String> titles(String token, String query) throws Exception {
        String json = mvc.perform(get(java.net.URI.create("/jobs" + query)).header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new ArrayList<>(JsonPath.<List<String>>read(json, "$[*].title"));
    }

    private static Instant ago(Duration duration) {
        return Instant.now().minus(duration);
    }

    private void seed() {
        Company acme = company("Acme Corp");
        Company globex = company("Globex");
        scraped("greenhouse", "Backend Engineer", acme, "Berlin, Germany", "remote", ago(Duration.ofDays(2)));
        scraped("lever", "Frontend Developer", globex, "New York", "hybrid", ago(Duration.ofDays(10)));
        scraped("lever", "Data Analyst", null, "Remote - EU", "onsite", ago(Duration.ofDays(40)));
    }

    @Test
    void qMatchesTitleOrCompanyNameCaseInsensitively() throws Exception {
        seed();

        assertThat(titles(jane, "?q=BACKEND")).containsExactly("Backend Engineer");
        assertThat(titles(jane, "?q=globex")).containsExactly("Frontend Developer");
        assertThat(titles(jane, "?q=corp")).containsExactly("Backend Engineer");
        assertThat(titles(jane, "?q=nothing-matches")).isEmpty();
    }

    @Test
    void jobsWithoutACompanyStillMatchOnTitle() throws Exception {
        seed();

        assertThat(titles(jane, "?q=analyst")).containsExactly("Data Analyst");
        assertThat(titles(jane, "?q=acme")).containsExactly("Backend Engineer");
    }

    @Test
    void sourceFilterMatchesExactly() throws Exception {
        seed();

        assertThat(titles(jane, "?source=lever")).containsExactly("Frontend Developer", "Data Analyst");
        assertThat(titles(jane, "?source=greenhouse")).containsExactly("Backend Engineer");
        assertThat(titles(jane, "?source=manual")).isEmpty();
    }

    @Test
    void remoteTypeFilterMatchesExactly() throws Exception {
        seed();

        assertThat(titles(jane, "?remoteType=remote")).containsExactly("Backend Engineer");
        assertThat(titles(jane, "?remoteType=hybrid")).containsExactly("Frontend Developer");
        assertThat(titles(jane, "?remoteType=onsite")).containsExactly("Data Analyst");
    }

    @Test
    void locationFilterIsACaseInsensitiveContains() throws Exception {
        seed();

        assertThat(titles(jane, "?location=berlin")).containsExactly("Backend Engineer");
        assertThat(titles(jane, "?location=EU")).containsExactly("Data Analyst");
        assertThat(titles(jane, "?location=mars")).isEmpty();
    }

    @Test
    void filtersCombineWithAnd() throws Exception {
        seed();

        assertThat(titles(jane, "?source=lever&remoteType=hybrid")).containsExactly("Frontend Developer");
        assertThat(titles(jane, "?source=lever&remoteType=remote")).isEmpty();
        assertThat(titles(jane, "?q=engineer&location=berlin&source=greenhouse&remoteType=remote&postedWithin=7"))
                .containsExactly("Backend Engineer");
        assertThat(titles(jane, "?q=engineer&location=new%20york")).isEmpty();
    }

    @Test
    void postedWithinBoundaries() throws Exception {
        scraped("lever", "Hours23", null, null, null, ago(Duration.ofHours(23)));
        scraped("lever", "Hours25", null, null, null, ago(Duration.ofHours(25)));
        scraped("lever", "Days6", null, null, null, ago(Duration.ofDays(6)));
        scraped("lever", "Days8", null, null, null, ago(Duration.ofDays(8)));
        scraped("lever", "Days29", null, null, null, ago(Duration.ofDays(29)));
        scraped("lever", "Days31", null, null, null, ago(Duration.ofDays(31)));

        assertThat(titles(jane, "?postedWithin=1")).containsExactly("Hours23");
        assertThat(titles(jane, "?postedWithin=7")).containsExactly("Hours23", "Hours25", "Days6");
        assertThat(titles(jane, "?postedWithin=30"))
                .containsExactly("Hours23", "Hours25", "Days6", "Days8", "Days29");
    }

    @Test
    void postedWithinFallsBackToCreatedAtForPastedJobs() throws Exception {
        paste(jane, "Just Pasted", "Acme", "Berlin", "remote");
        scraped("lever", "Old Scraped", null, null, null, ago(Duration.ofDays(20)));

        assertThat(titles(jane, "?postedWithin=1")).containsExactly("Just Pasted");
        assertThat(titles(jane, "?postedWithin=30")).containsExactly("Just Pasted", "Old Scraped");
    }

    @Test
    void likeWildcardsInUserInputAreLiteral() throws Exception {
        scraped("lever", "Grow 100% faster", null, "A_B", null, ago(Duration.ofDays(1)));
        scraped("lever", "Grow 1000 faster", null, "AxB", null, ago(Duration.ofDays(2)));
        scraped("lever", "Path c:\\dir", null, null, null, ago(Duration.ofDays(3)));

        assertThat(titles(jane, "?q=100%25")).containsExactly("Grow 100% faster");
        assertThat(titles(jane, "?q=%25")).containsExactly("Grow 100% faster");
        assertThat(titles(jane, "?location=A_B")).containsExactly("Grow 100% faster");
        assertThat(titles(jane, "?q=_")).isEmpty();
        assertThat(titles(jane, "?q=c:%5Cdir")).containsExactly("Path c:\\dir");
        assertThat(titles(jane, "?q=%5C")).containsExactly("Path c:\\dir");
    }

    @Test
    void searchTextIsNeverInterpretedAsQueryLanguage() throws Exception {
        seed();

        assertThat(titles(jane, "?q=%27%20OR%201%3D1%20--")).isEmpty();
        assertThat(titles(jane, "?location=%27%3B%20DROP%20TABLE%20jobs%3B%20--")).isEmpty();
        assertThat(jobs.count()).isEqualTo(3);
    }

    @Test
    void offsetPagesHaveStableOrderAndNoOverlap() throws Exception {
        Instant same = ago(Duration.ofDays(1));
        for (int i = 0; i < 12; i++) {
            // Equal timestamps force the id tie-breaker to decide the order.
            scraped("lever", "Job " + i, null, null, null, same);
        }

        List<String> all = titles(jane, "?limit=100");
        List<String> paged = new ArrayList<>();
        for (int offset = 0; offset < 12; offset += 5) {
            paged.addAll(titles(jane, "?limit=5&offset=" + offset));
        }

        assertThat(all).hasSize(12).doesNotHaveDuplicates();
        assertThat(paged).isEqualTo(all);
        assertThat(all.get(0)).isEqualTo("Job 11");
        assertThat(titles(jane, "?limit=5&offset=12")).isEmpty();
        assertThat(titles(jane, "?limit=5&offset=10")).containsExactly("Job 1", "Job 0");
    }

    @Test
    void offsetWorksTogetherWithFilters() throws Exception {
        for (int i = 0; i < 6; i++) {
            scraped("lever", "Lever " + i, null, null, null, ago(Duration.ofHours(i + 1)));
            scraped("greenhouse", "Green " + i, null, null, null, ago(Duration.ofHours(i + 1)));
        }

        assertThat(titles(jane, "?source=lever&limit=2&offset=2")).containsExactly("Lever 2", "Lever 3");
        assertThat(titles(jane, "?source=lever&limit=2&offset=4")).containsExactly("Lever 4", "Lever 5");
        assertThat(titles(jane, "?source=lever&limit=2&offset=6")).isEmpty();
    }

    @Test
    void ordersByPostedAtElseCreatedAtUnderFilters() throws Exception {
        scraped("lever", "Old", null, null, null, ago(Duration.ofDays(5)));
        paste(jane, "Fresh Paste", "Acme", "Berlin", "remote");
        scraped("lever", "Recent", null, null, null, ago(Duration.ofDays(1)));

        assertThat(titles(jane, "?postedWithin=7")).containsExactly("Fresh Paste", "Recent", "Old");
        assertThat(titles(jane, "?scope=pasted&q=acme")).containsExactly("Fresh Paste");
    }

    @Test
    void anotherUsersPastedJobsNeverAppearUnderAnyFilterOrScope() throws Exception {
        paste(john, "Johns Secret Job", "SecretCo", "Hidden City", "remote");
        scraped("lever", "Public Job", company("Acme"), "Berlin", "remote", ago(Duration.ofDays(1)));
        paste(jane, "Janes Job", "Acme", "Berlin", "remote");

        String[] queries = {"", "?q=secret", "?q=johns", "?q=secretco", "?q=job", "?source=manual",
                "?remoteType=remote", "?location=hidden", "?location=city", "?postedWithin=1", "?postedWithin=30",
                "?scope=pasted", "?scope=pasted&q=secret", "?scope=pasted&source=manual",
                "?q=job&source=manual&remoteType=remote&location=hidden&postedWithin=7&offset=0&limit=100"};
        for (String query : queries) {
            assertThat(titles(jane, query)).as("jane " + query).doesNotContain("Johns Secret Job");
        }
        assertThat(titles(jane, "?q=secret")).isEmpty();
        assertThat(titles(jane, "?source=manual")).containsExactly("Janes Job");
        assertThat(titles(john, "?source=manual")).containsExactly("Johns Secret Job");
        assertThat(titles(john, "?q=janes")).isEmpty();
    }

    @Test
    void aUserWithoutAProfileSeesNoPastedJobsEvenWithFilters() throws Exception {
        paste(jane, "Janes Job", "Acme", "Berlin", "remote");
        String json = mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"noprofile@example.com\",\"password\":\"secret-pass\"}"))
                .andReturn().getResponse().getContentAsString();
        String token = "Bearer " + JsonPath.read(json, "$.token");

        assertThat(titles(token, "?source=manual")).isEmpty();
        assertThat(titles(token, "?scope=pasted&q=janes")).isEmpty();
    }

    @Test
    void invalidValuesReturn400WithStatusAndMessage() throws Exception {
        String[] bad = {"q=", "q=%20%20", "q=" + "a".repeat(101), "source=indeed", "source=", "remoteType=office",
                "remoteType=", "location=", "location=" + "a".repeat(101), "postedWithin=2", "postedWithin=0",
                "postedWithin=abc", "postedWithin=", "offset=-1", "offset=abc", "offset=1.5",
                "q=ok&offset=-5", "scope=pasted&source=nope"};
        for (String query : bad) {
            mvc.perform(get(java.net.URI.create("/jobs?" + query)).header("Authorization", jane))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").isString());
        }
    }

    @Test
    void filtersStillNeedAToken() throws Exception {
        mvc.perform(get("/jobs?q=dev")).andExpect(status().isUnauthorized());
    }
}
