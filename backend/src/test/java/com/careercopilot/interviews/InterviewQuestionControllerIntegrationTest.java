package com.careercopilot.interviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careercopilot.applications.ApplicationRepository;
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
class InterviewQuestionControllerIntegrationTest {

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
    private InterviewQuestionRepository questions;

    @Autowired
    private RateLimiter rateLimiter;

    @BeforeEach
    @AfterEach
    void cleanState() {
        questions.deleteAll();
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
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(json, "$.token");
    }

    private String tokenWithProfile(String email) throws Exception {
        String token = signupToken(email);
        mvc.perform(put("/profile").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Jane Doe\"}")).andExpect(status().isOk());
        return token;
    }

    private long profileIdOf(String email) {
        return profiles.findByUserId(users.findByEmail(email).orElseThrow().getId()).orElseThrow().getId();
    }

    private long scrapedJob() {
        Company company = companies.save(new Company("Stripe"));
        Job job = new Job("greenhouse", "Backend Engineer", "We need Java, Docker and Kubernetes.");
        job.setExternalId("be-1");
        job.setCompanyId(company.getId());
        return jobs.save(job).getId();
    }

    private long pastedJob(String token) throws Exception {
        String json = mvc.perform(post("/jobs/match").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Private role\",\"companyName\":\"Acme\","
                                + "\"description\":\"A private description with enough text to be accepted.\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.job.id")).longValue();
    }

    private List<Integer> ids(String json) {
        return JsonPath.read(json, "$.questions[*].id");
    }

    @Test
    void firstGetGeneratesBetween20And30QuestionsWithTheDocumentedShape() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        long jobId = scrapedJob();

        mvc.perform(get("/jobs/" + jobId + "/interview-questions").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.jobTitle").value("Backend Engineer"))
                .andExpect(jsonPath("$.company").value("Stripe"))
                .andExpect(jsonPath("$.questions.length()", greaterThanOrEqualTo(20)))
                .andExpect(jsonPath("$.questions.length()", lessThanOrEqualTo(30)))
                .andExpect(jsonPath("$.questions[0].id").isNumber())
                .andExpect(jsonPath("$.questions[0].question").isNotEmpty())
                .andExpect(jsonPath("$.questions[0].category").isNotEmpty())
                .andExpect(jsonPath("$.questions[?(@.category=='technical')]").isNotEmpty())
                .andExpect(jsonPath("$.questions[?(@.category=='company')]").isNotEmpty())
                // No resume yet, so nothing about the resume.
                .andExpect(jsonPath("$.questions[?(@.category=='resume_gap')]").isEmpty());
        assertThat(questions.count()).isBetween(20L, 30L);
    }

    @Test
    void repeatCallsReturnTheSameStoredListAndPostReplacesIt() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        long jobId = scrapedJob();
        String path = "/jobs/" + jobId + "/interview-questions";
        String first = mvc.perform(get(path).header("Authorization", token)).andReturn().getResponse()
                .getContentAsString();
        String second = mvc.perform(get(path).header("Authorization", token)).andReturn().getResponse()
                .getContentAsString();

        assertThat(second).isEqualTo(first);
        long stored = questions.count();

        String replaced = mvc.perform(post(path).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions.length()", greaterThanOrEqualTo(20)))
                .andReturn().getResponse().getContentAsString();

        assertThat(ids(replaced)).doesNotContainAnyElementsOf(ids(first));
        assertThat(questions.count()).isEqualTo(stored);
        // Same inputs, so the new list has the same questions, and GET now returns the replacement.
        assertThat((List<String>) JsonPath.read(replaced, "$.questions[*].question"))
                .isEqualTo(JsonPath.read(first, "$.questions[*].question"));
        String afterReplace = mvc.perform(get(path).header("Authorization", token)).andReturn().getResponse()
                .getContentAsString();
        assertThat(ids(afterReplace)).isEqualTo(ids(replaced));
    }

    @Test
    void aBaseResumeAddsResumeGapQuestionsAndRegenerationPicksThemUp() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        long jobId = scrapedJob();
        String path = "/jobs/" + jobId + "/interview-questions";
        mvc.perform(get(path).header("Authorization", token))
                .andExpect(jsonPath("$.questions[?(@.category=='resume_gap')]").isEmpty());

        Resume resume = new Resume(profileIdOf("jane@example.com"), "resumes/x.pdf", "cv.pdf", "application/pdf",
                "Java developer");
        resume.setSkills("[\"Java\"]");
        resumes.save(resume);

        // The stored list is kept until the user asks for a new one.
        mvc.perform(get(path).header("Authorization", token))
                .andExpect(jsonPath("$.questions[?(@.category=='resume_gap')]").isEmpty());
        mvc.perform(post(path).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[?(@.category=='resume_gap')]").isNotEmpty())
                .andExpect(jsonPath("$.questions[?(@.question =~ /.*Docker.*not see.*/i)]").isNotEmpty());
    }

    @Test
    void listsAreStoredPerProfile() throws Exception {
        String jane = tokenWithProfile("jane@example.com");
        String other = tokenWithProfile("other@example.com");
        long jobId = scrapedJob();
        String path = "/jobs/" + jobId + "/interview-questions";

        String janes = mvc.perform(get(path).header("Authorization", jane)).andReturn().getResponse()
                .getContentAsString();
        String others = mvc.perform(get(path).header("Authorization", other)).andReturn().getResponse()
                .getContentAsString();

        assertThat(ids(janes)).doesNotContainAnyElementsOf(ids(others));
        assertThat(questions.findByProfileIdAndJobIdOrderByPositionAscIdAsc(profileIdOf("jane@example.com"), jobId))
                .hasSameSizeAs(ids(janes));
        // Regenerating for one user leaves the other's list alone.
        mvc.perform(post(path).header("Authorization", jane)).andExpect(status().isOk());
        String othersAgain = mvc.perform(get(path).header("Authorization", other)).andReturn().getResponse()
                .getContentAsString();
        assertThat(ids(othersAgain)).isEqualTo(ids(others));
    }

    @Test
    void anotherUsersPastedJobIsNotFoundAndNothingIsStored() throws Exception {
        String owner = tokenWithProfile("owner@example.com");
        String other = tokenWithProfile("other@example.com");
        long jobId = pastedJob(owner);
        String path = "/jobs/" + jobId + "/interview-questions";

        mvc.perform(get(path).header("Authorization", other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Job not found."));
        mvc.perform(post(path).header("Authorization", other)).andExpect(status().isNotFound());
        assertThat(questions.count()).isZero();

        // The owner can use their own pasted job.
        mvc.perform(get(path).header("Authorization", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobTitle").value("Private role"))
                .andExpect(jsonPath("$.company").value("Acme"));
    }

    @Test
    void unknownJobAndMissingProfileAre404() throws Exception {
        String withProfile = tokenWithProfile("jane@example.com");
        String noProfile = signupToken("np@example.com");
        long jobId = scrapedJob();

        mvc.perform(get("/jobs/999999/interview-questions").header("Authorization", withProfile))
                .andExpect(status().isNotFound());
        mvc.perform(get("/jobs/" + jobId + "/interview-questions").header("Authorization", noProfile))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Create your profile first."));
        mvc.perform(post("/jobs/" + jobId + "/interview-questions").header("Authorization", noProfile))
                .andExpect(status().isNotFound());
    }

    @Test
    void endpointsNeedATokenAndOtherMethodsAreRejected() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        long jobId = scrapedJob();

        mvc.perform(get("/jobs/" + jobId + "/interview-questions")).andExpect(status().isUnauthorized());
        mvc.perform(post("/jobs/" + jobId + "/interview-questions")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/jobs/" + jobId + "/interview-questions").header("Authorization", token))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void deletingTheJobRemovesItsQuestions() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        long jobId = scrapedJob();
        mvc.perform(get("/jobs/" + jobId + "/interview-questions").header("Authorization", token))
                .andExpect(status().isOk());
        assertThat(questions.count()).isPositive();

        jobs.deleteById(jobId);

        assertThat(questions.count()).isZero();
    }
}
