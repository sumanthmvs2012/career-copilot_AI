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
class JobControllerIntegrationTest {

    private static final String PROFILE = "{\"fullName\":\"Jane Doe\"}";
    private static final String VALID = "{\"title\":\"Backend Engineer\",\"companyName\":\"Acme\","
            + "\"description\":\"Build and run backend services in Java.\",\"location\":\"Berlin\","
            + "\"remoteType\":\"hybrid\",\"sourceUrl\":\"https://example.com/job\"}";

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

    @AfterEach
    void removeJobsSoOtherTestsCanClearProfiles() {
        jobs.deleteAll();
        companies.deleteAll();
    }

    @BeforeEach
    void cleanState() {
        jobs.deleteAll();
        companies.deleteAll();
        resumes.deleteAll();
        profiles.deleteAll();
        users.deleteAll();
        rateLimiter.reset();
    }

    private String signupToken(String email) throws Exception {
        String json = mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret-pass\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(json, "$.token");
    }

    private String tokenWithProfile(String email) throws Exception {
        String token = signupToken(email);
        mvc.perform(put("/profile").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(PROFILE)).andExpect(status().isOk());
        return token;
    }

    private void match(String token, String body, int expectedStatus) throws Exception {
        mvc.perform(post("/jobs/match").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void matchSavesTheJobAndReturns201WithANullMatch() throws Exception {
        String token = tokenWithProfile("jane@example.com");

        mvc.perform(post("/jobs/match").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.job.id").isNumber())
                .andExpect(jsonPath("$.job.source").value("manual"))
                .andExpect(jsonPath("$.job.title").value("Backend Engineer"))
                .andExpect(jsonPath("$.job.companyName").value("Acme"))
                .andExpect(jsonPath("$.job.remoteType").value("hybrid"))
                .andExpect(jsonPath("$.job.description").value("Build and run backend services in Java."))
                .andExpect(jsonPath("$.job.postedAt").value((Object) null))
                .andExpect(jsonPath("$.match").value((Object) null))
                .andExpect(jsonPath("$.message").isString());

        assertThat(jobs.count()).isEqualTo(1);
        assertThat(jobs.findAll().get(0).getCreatedByProfileId()).isNotNull();
        assertThat(companies.count()).isEqualTo(1);
    }

    @Test
    void shortDescriptionReturns400WithAFieldError() throws Exception {
        String token = tokenWithProfile("jane@example.com");

        mvc.perform(post("/jobs/match").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"too short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.description").isString());
        assertThat(jobs.count()).isZero();
    }

    @Test
    void invalidRemoteTypeReturns400() throws Exception {
        String token = tokenWithProfile("jane@example.com");

        mvc.perform(post("/jobs/match").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Build and run backend services in Java.\","
                                + "\"remoteType\":\"office\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.remoteType").isString());
    }

    @Test
    void matchWithoutAProfileReturns404() throws Exception {
        String token = signupToken("jane@example.com");

        mvc.perform(post("/jobs/match").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        assertThat(jobs.count()).isZero();
    }

    @Test
    void listShowsTheCallersJobButNotAnotherUsersJob() throws Exception {
        String first = tokenWithProfile("jane@example.com");
        String second = tokenWithProfile("john@example.com");
        match(first, VALID, 201);

        mvc.perform(get("/jobs").header("Authorization", first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Backend Engineer"))
                .andExpect(jsonPath("$[0].companyName").value("Acme"))
                .andExpect(jsonPath("$[0].description").value("Build and run backend services in Java."))
                .andExpect(jsonPath("$[0].postedAt").value((Object) null));

        mvc.perform(get("/jobs").header("Authorization", second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void withoutATokenBothEndpointsReturn401() throws Exception {
        mvc.perform(get("/jobs")).andExpect(status().isUnauthorized());
        mvc.perform(post("/jobs/match").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnauthorized());
    }

    private Job scraped(String title, java.time.Instant postedAt) {
        Job job = new Job("greenhouse", title, "Scraped description");
        job.setExternalId(title);
        job.setPostedAt(postedAt);
        job.setSourceUrl("https://example.com/" + title.replace(' ', '-'));
        return jobs.save(job);
    }

    @Test
    void scopeFiltersPastedAndScrapedAndNeverShowsAnotherUsersPastedJobs() throws Exception {
        String jane = tokenWithProfile("jane@example.com");
        String john = tokenWithProfile("john@example.com");
        match(jane, VALID, 201);
        match(john, VALID.replace("Backend Engineer", "Johns Secret Job"), 201);
        scraped("Scraped One", java.time.Instant.parse("2026-11-14T10:00:00Z"));

        for (String scope : new String[] {"", "?scope=pasted", "?scope=scraped"}) {
            mvc.perform(get("/jobs" + scope).header("Authorization", jane))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.title == 'Johns Secret Job')]").isEmpty());
        }
        mvc.perform(get("/jobs?scope=pasted").header("Authorization", jane))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Backend Engineer"));
        mvc.perform(get("/jobs?scope=scraped").header("Authorization", jane))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Scraped One"))
                .andExpect(jsonPath("$[0].sourceUrl").value("https://example.com/Scraped-One"))
                .andExpect(jsonPath("$[0].postedAt").isString());
        mvc.perform(get("/jobs").header("Authorization", jane))
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/jobs?scope=scraped").header("Authorization", john))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void jobsAreOrderedByPostedAtElseCreatedAtNewestFirstAndLimited() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        scraped("Old", java.time.Instant.parse("2020-01-01T00:00:00Z"));
        scraped("Recent", java.time.Instant.parse("2026-11-10T00:00:00Z"));
        scraped("Future", java.time.Instant.parse("2999-01-01T00:00:00Z"));
        match(token, VALID, 201);

        mvc.perform(get("/jobs").header("Authorization", token))
                .andExpect(jsonPath("$[0].title").value("Future"))
                .andExpect(jsonPath("$[1].title").value("Backend Engineer"))
                .andExpect(jsonPath("$[2].title").value("Recent"))
                .andExpect(jsonPath("$[3].title").value("Old"));
        mvc.perform(get("/jobs?limit=2").header("Authorization", token))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Future"));
    }

    @Test
    void limitDefaultsTo50AndAcceptsUpTo100() throws Exception {
        String token = signupToken("jane@example.com");
        for (int i = 0; i < 105; i++) {
            scraped("Job " + i, java.time.Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i));
        }

        mvc.perform(get("/jobs").header("Authorization", token))
                .andExpect(jsonPath("$.length()").value(50));
        mvc.perform(get("/jobs?limit=100").header("Authorization", token))
                .andExpect(jsonPath("$.length()").value(100));
    }

    @Test
    void invalidScopeOrLimitReturns400() throws Exception {
        String token = signupToken("jane@example.com");

        for (String query : new String[] {"scope=all", "scope=other", "limit=0", "limit=101", "limit=-1", "limit=abc"}) {
            mvc.perform(get("/jobs?" + query).header("Authorization", token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").isString());
        }
    }
}
