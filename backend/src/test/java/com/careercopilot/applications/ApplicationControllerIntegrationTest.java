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
import com.careercopilot.mail.EmailLog;
import com.careercopilot.mail.EmailLogRepository;
import com.careercopilot.profiles.ProfileRepository;
import com.careercopilot.resumes.Resume;
import com.careercopilot.resumes.ResumeRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
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
    private EmailLogRepository emailLogs;

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

    private int draft(String token, long jobId) throws Exception {
        String json = mvc.perform(post("/applications").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(jobId, "")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    @Test
    void submitMovesADraftToAppliedWithoutABody() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        int id = draft(token, scrapedJob("Java Dev", "Stripe"));

        mvc.perform(post("/applications/" + id + "/submit").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("applied"))
                .andExpect(jsonPath("$.appliedAt").isNotEmpty())
                .andExpect(jsonPath("$.submittedBy").value("user"))
                .andExpect(jsonPath("$.jobTitle").value("Java Dev"));
    }

    @Test
    void submitSavesTheCoverLetterFromTheBody() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        int id = draft(token, scrapedJob("Java Dev", "Stripe"));

        mvc.perform(post("/applications/" + id + "/submit").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"coverLetter\":\"Dear team\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverLetter").value("Dear team"));
        mvc.perform(get("/applications").header("Authorization", token))
                .andExpect(jsonPath("$[0].coverLetter").value("Dear team"))
                .andExpect(jsonPath("$[0].status").value("applied"));
    }

    @Test
    void submitTwiceOrAfterAnyOtherStatusReturns409() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        int id = draft(token, scrapedJob("Java Dev", "Stripe"));
        mvc.perform(post("/applications/" + id + "/submit").header("Authorization", token))
                .andExpect(status().isOk());

        mvc.perform(post("/applications/" + id + "/submit").header("Authorization", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Only a draft can be submitted."));

        mvc.perform(put("/applications/" + id).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"withdrawn\"}")).andExpect(status().isOk());
        mvc.perform(post("/applications/" + id + "/submit").header("Authorization", token))
                .andExpect(status().isConflict());
    }

    @Test
    void submitIsNotFoundForAnotherUsersApplicationAndLeavesItUntouched() throws Exception {
        String owner = tokenWithProfile("owner@example.com");
        String other = tokenWithProfile("other@example.com");
        int id = draft(owner, scrapedJob("Java Dev", "Stripe"));

        mvc.perform(post("/applications/" + id + "/submit").header("Authorization", other))
                .andExpect(status().isNotFound());
        mvc.perform(post("/applications/999999/submit").header("Authorization", other))
                .andExpect(status().isNotFound());
        mvc.perform(get("/applications").header("Authorization", owner))
                .andExpect(jsonPath("$[0].status").value("draft"));
    }

    @Test
    void submitRejectsATooLongCoverLetterAndNeedsAToken() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        int id = draft(token, scrapedJob("Java Dev", "Stripe"));

        mvc.perform(post("/applications/" + id + "/submit").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coverLetter\":\"" + "a".repeat(10001) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/applications/" + id + "/submit")).andExpect(status().isUnauthorized());
    }

    @Test
    void emailFieldsComeFromEmployerRepliesInEmailLogs() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        int quiet = draft(token, scrapedJob("Quiet", "Stripe"));
        int replied = draft(token, scrapedJob("Replied", "Acme"));
        EmailLog older = new EmailLog((long) replied, EmailLog.TYPE_REPLY, Instant.parse("2026-11-10T10:00:00Z"),
                EmailLog.STATUS_RECEIVED);
        older.setClassification("other");
        EmailLog newer = new EmailLog((long) replied, EmailLog.TYPE_REPLY, Instant.parse("2026-11-12T10:00:00Z"),
                EmailLog.STATUS_RECEIVED);
        newer.setClassification("interview");
        // A reminder is not an employer reply, so it must not flip emailSent.
        EmailLog reminder = new EmailLog((long) quiet, EmailLog.TYPE_REMINDER, Instant.parse("2026-11-13T10:00:00Z"),
                EmailLog.STATUS_NEW);
        emailLogs.saveAll(java.util.List.of(older, newer, reminder));

        mvc.perform(get("/applications").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobTitle").value("Replied"))
                .andExpect(jsonPath("$[0].emailSent").value(true))
                .andExpect(jsonPath("$[0].emailSentAt").value("2026-11-12T10:00:00Z"))
                .andExpect(jsonPath("$[0].emailClassification").value("interview"))
                .andExpect(jsonPath("$[1].emailSent").value(false))
                .andExpect(jsonPath("$[1].emailSentAt").isEmpty())
                .andExpect(jsonPath("$[1].emailClassification").isEmpty());
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
