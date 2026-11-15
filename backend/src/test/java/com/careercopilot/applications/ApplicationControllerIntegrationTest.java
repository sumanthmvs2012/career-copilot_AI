package com.careercopilot.applications;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careercopilot.auth.UserRepository;
import com.careercopilot.common.ratelimit.RateLimiter;
import com.careercopilot.jobs.Company;
import com.careercopilot.jobs.CompanyRepository;
import com.careercopilot.jobs.Job;
import com.careercopilot.jobs.JobRepository;
import com.careercopilot.profiles.ProfileRepository;
import com.careercopilot.resumes.Resume;
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
class ApplicationControllerIntegrationTest {

    private static final String PROFILE = "{\"fullName\":\"Jane Doe\"}";

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
    private ApplicationRepository applications;

    @Autowired
    private RateLimiter rateLimiter;

    @BeforeEach
    @AfterEach
    void cleanState() {
        applications.deleteAll();
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

    private long scrapedJob(String title, String companyName) {
        Company company = companies.save(new Company(companyName));
        Job job = new Job("greenhouse", title, "Description");
        job.setExternalId(title);
        job.setCompanyId(company.getId());
        return jobs.save(job).getId();
    }

    private long pastedJob(String token, String title) throws Exception {
        String json = mvc.perform(post("/jobs/match").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"companyName\":\"Acme\",\"description\":\"Build and ship great software\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.job.id")).longValue();
    }

    private long resumeOf(String email) {
        Long profileId = profiles.findAll().stream()
                .filter(p -> users.findById(p.getUserId()).orElseThrow().getEmail().equals(email))
                .findFirst().orElseThrow().getId();
        return resumes.save(new Resume(profileId, "resumes/x.pdf", "cv.pdf", "application/pdf", "text")).getId();
    }

    private String createBody(long jobId, String extra) {
        return "{\"jobId\":" + jobId + (extra.isEmpty() ? "" : "," + extra) + "}";
    }

    @Test
    void createsADraftApplicationForAScrapedJob() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        long jobId = scrapedJob("Java Dev", "Stripe");

        mvc.perform(post("/applications").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(jobId, "")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.jobTitle").value("Java Dev"))
                .andExpect(jsonPath("$.company").value("Stripe"))
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.appliedAt").isEmpty())
                .andExpect(jsonPath("$.submittedBy").value("user"))
                .andExpect(jsonPath("$.emailSent").value(false))
                .andExpect(jsonPath("$.emailSentAt").isEmpty());
    }

    @Test
    void createWithAppliedStatusSetsAppliedAt() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        long jobId = pastedJob(token, "Own job");
        long resumeId = resumeOf("jane@example.com");

        mvc.perform(post("/applications").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(jobId, "\"status\":\"applied\",\"coverLetter\":\"Hi\",\"resumeId\":" + resumeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("applied"))
                .andExpect(jsonPath("$.appliedAt").isNotEmpty())
                .andExpect(jsonPath("$.resumeId").value(resumeId))
                .andExpect(jsonPath("$.coverLetter").value("Hi"));
    }

    @Test
    void secondApplicationForTheSameJobReturns409() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        long jobId = scrapedJob("Java Dev", "Stripe");
        mvc.perform(post("/applications").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(createBody(jobId, ""))).andExpect(status().isCreated());

        mvc.perform(post("/applications").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(jobId, "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("You already track this job."));
    }

    @Test
    void unknownJobReturns404() throws Exception {
        String token = tokenWithProfile("jane@example.com");

        mvc.perform(post("/applications").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(999999, "")))
                .andExpect(status().isNotFound());
    }

    @Test
    void anotherUsersPastedJobReturns404() throws Exception {
        String owner = tokenWithProfile("owner@example.com");
        String other = tokenWithProfile("other@example.com");
        long jobId = pastedJob(owner, "Private job");

        mvc.perform(post("/applications").header("Authorization", other)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(jobId, "")))
                .andExpect(status().isNotFound());
    }

    @Test
    void anotherUsersResumeReturns404() throws Exception {
        String jane = tokenWithProfile("jane@example.com");
        tokenWithProfile("other@example.com");
        long otherResume = resumeOf("other@example.com");
        long jobId = scrapedJob("Java Dev", "Stripe");

        mvc.perform(post("/applications").header("Authorization", jane)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(jobId, "\"resumeId\":" + otherResume)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createWithoutProfileReturns404() throws Exception {
        String token = signupToken("noprofile@example.com");
        long jobId = scrapedJob("Java Dev", "Stripe");

        mvc.perform(post("/applications").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(jobId, "")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Create your profile first."));
    }

    @Test
    void createRejectsBadInput() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        long jobId = scrapedJob("Java Dev", "Stripe");

        mvc.perform(post("/applications").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(jobId, "\"status\":\"offer\"")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/applications").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/applications").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(jobId, "\"coverLetter\":\"" + "a".repeat(10001) + "\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listReturnsOnlyTheCallersApplicationsNewestFirst() throws Exception {
        String jane = tokenWithProfile("jane@example.com");
        String other = tokenWithProfile("other@example.com");
        long first = scrapedJob("First", "Stripe");
        long second = scrapedJob("Second", "Stripe");
        long third = scrapedJob("Third", "Stripe");
        for (long jobId : new long[] {first, second}) {
            mvc.perform(post("/applications").header("Authorization", jane)
                    .contentType(MediaType.APPLICATION_JSON).content(createBody(jobId, ""))).andExpect(status().isCreated());
        }
        mvc.perform(post("/applications").header("Authorization", other)
                .contentType(MediaType.APPLICATION_JSON).content(createBody(third, ""))).andExpect(status().isCreated());

        mvc.perform(get("/applications").header("Authorization", jane))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].jobTitle").value("Second"))
                .andExpect(jsonPath("$[1].jobTitle").value("First"));
        mvc.perform(get("/applications").header("Authorization", other))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].jobTitle").value("Third"));
    }

    @Test
    void updateChangesStatusAndSetsAppliedAtOnce() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        long jobId = scrapedJob("Java Dev", "Stripe");
        String json = mvc.perform(post("/applications").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(jobId, "")))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(json, "$.id");

        String applied = mvc.perform(put("/applications/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"applied\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("applied"))
                .andExpect(jsonPath("$.appliedAt").isNotEmpty())
                .andExpect(jsonPath("$.company").value("Stripe"))
                .andReturn().getResponse().getContentAsString();
        String appliedAt = JsonPath.read(applied, "$.appliedAt");

        mvc.perform(put("/applications/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"interviewing\",\"coverLetter\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("interviewing"))
                .andExpect(jsonPath("$.coverLetter").value("Updated"))
                .andExpect(jsonPath("$.appliedAt").value(appliedAt));
    }

    @Test
    void updatingAnotherUsersApplicationReturns404() throws Exception {
        String owner = tokenWithProfile("owner@example.com");
        String other = tokenWithProfile("other@example.com");
        long jobId = scrapedJob("Java Dev", "Stripe");
        String json = mvc.perform(post("/applications").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(jobId, "")))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(json, "$.id");

        mvc.perform(put("/applications/" + id).header("Authorization", other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"applied\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRejectsInvalidStatusAndEmptyBody() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        long jobId = scrapedJob("Java Dev", "Stripe");
        String json = mvc.perform(post("/applications").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(jobId, "")))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(json, "$.id");

        mvc.perform(put("/applications/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ghosted\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/applications/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void endpointsRequireAToken() throws Exception {
        mvc.perform(get("/applications")).andExpect(status().isUnauthorized());
        mvc.perform(post("/applications").contentType(MediaType.APPLICATION_JSON).content("{\"jobId\":1}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/applications/1").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"offer\"}"))
                .andExpect(status().isUnauthorized());
    }
}
