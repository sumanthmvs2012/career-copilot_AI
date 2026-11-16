package com.careercopilot.matches;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
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
class MatchControllerIntegrationTest {

    private static final String PROFILE = "{\"fullName\":\"Jane Doe\",\"location\":\"Berlin, Germany\","
            + "\"remoteOk\":true}";
    private static final String PASTE = "{\"title\":\"Backend Engineer\",\"description\":"
            + "\"We need Java, Docker and Kubernetes experience for our payments platform.\"}";

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
    private JobMatchRepository matches;

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private RateLimiter rateLimiter;

    @BeforeEach
    @AfterEach
    void cleanState() {
        matches.deleteAll();
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

    private long profileIdOf(String email) {
        return profiles.findAll().stream()
                .filter(p -> users.findById(p.getUserId()).orElseThrow().getEmail().equals(email))
                .findFirst().orElseThrow().getId();
    }

    private Resume baseResume(String email, String text, String skillsJson) {
        Resume resume = new Resume(profileIdOf(email), "resumes/x.pdf", "cv.pdf", "application/pdf", text);
        resume.setSkills(skillsJson);
        return resumes.save(resume);
    }

    private long scrapedJob(String title, String description) {
        Company company = companies.save(new Company("Acme"));
        Job job = new Job("greenhouse", title, description);
        job.setExternalId(title);
        job.setCompanyId(company.getId());
        job.setRemoteType("remote");
        return jobs.save(job).getId();
    }

    private long pastedJob(String token) throws Exception {
        String json = mvc.perform(post("/jobs/match").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(PASTE))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.job.id")).longValue();
    }

    @Test
    void matchesTheBaseResumeAgainstAScrapedJob() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        Resume resume = baseResume("jane@example.com", "Java developer. Payments experience.", null);
        long jobId = scrapedJob("Platform Engineer", "We use Java, Docker and Kubernetes at scale.");

        mvc.perform(post("/jobs/" + jobId + "/match").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.resumeId").value(resume.getId()))
                .andExpect(jsonPath("$.score").value(33))
                .andExpect(jsonPath("$.matchedSkills[0]").value("Java"))
                .andExpect(jsonPath("$.missingSkills[0]").value("Docker"))
                .andExpect(jsonPath("$.missingSkills[1]").value("Kubernetes"))
                .andExpect(jsonPath("$.nearMatchedSkills.length()").value(0))
                .andExpect(jsonPath("$.suggestedHeadline").value("Platform Engineer"))
                .andExpect(jsonPath("$.locationFit.status").value("match"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
        // The older resume had no skills; they were extracted and saved on the way.
        assertThat(resumes.findById(resume.getId()).orElseThrow().getSkills()).isEqualTo("[\"Java\"]");
        assertThat(jobs.findById(jobId).orElseThrow().getSkills()).isEqualTo("[\"Java\",\"Docker\",\"Kubernetes\"]");
    }

    @Test
    void reportsANearMatchFromTheDictionary() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        baseResume("jane@example.com", "Mobile developer", "[\"React Native\"]");
        long jobId = scrapedJob("Frontend", "Build UIs in React.");

        mvc.perform(post("/jobs/" + jobId + "/match").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(0))
                .andExpect(jsonPath("$.nearMatchedSkills[0].jobSkill").value("React"))
                .andExpect(jsonPath("$.nearMatchedSkills[0].resumeSkill").value("React Native"));
    }

    @Test
    void runningAgainReplacesTheStoredRow() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        Resume resume = baseResume("jane@example.com", "text", "[\"Java\"]");
        long jobId = scrapedJob("Dev", "Java and Docker needed.");

        mvc.perform(post("/jobs/" + jobId + "/match").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(50));
        assertThat(matches.count()).isEqualTo(1);

        resume.setSkills("[\"Java\",\"Docker\"]");
        resumes.save(resume);
        mvc.perform(post("/jobs/" + jobId + "/match").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(100));

        assertThat(matches.count()).isEqualTo(1);
        mvc.perform(get("/jobs/" + jobId + "/match").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(100));
    }

    @Test
    void getIsNotFoundUntilAMatchWasStoredThenReturnsIt() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        baseResume("jane@example.com", "text", "[\"Java\"]");
        long jobId = scrapedJob("Dev", "Java needed.");

        mvc.perform(get("/jobs/" + jobId + "/match").header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("No match yet for this job."));

        mvc.perform(post("/jobs/" + jobId + "/match").header("Authorization", token)).andExpect(status().isOk());

        mvc.perform(get("/jobs/" + jobId + "/match").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.score").value(100))
                .andExpect(jsonPath("$.matchedSkills[0]").value("Java"));
    }

    @Test
    void withoutAResumeTheMatchIsAConflict() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        long jobId = scrapedJob("Dev", "Java needed.");

        mvc.perform(post("/jobs/" + jobId + "/match").header("Authorization", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Upload a resume first to see your match."));
    }

    @Test
    void withoutAProfileTheMatchIsNotFound() throws Exception {
        String token = signupToken("noprofile@example.com");
        long jobId = scrapedJob("Dev", "Java needed.");

        mvc.perform(post("/jobs/" + jobId + "/match").header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Create your profile first."));
    }

    @Test
    void anotherUsersPastedJobIsNotFound() throws Exception {
        String ownerToken = tokenWithProfile("owner@example.com");
        long jobId = pastedJob(ownerToken);
        String otherToken = tokenWithProfile("other@example.com");
        baseResume("other@example.com", "text", "[\"Java\"]");

        mvc.perform(post("/jobs/" + jobId + "/match").header("Authorization", otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Job not found."));
        mvc.perform(get("/jobs/" + jobId + "/match").header("Authorization", otherToken))
                .andExpect(status().isNotFound());
        assertThat(matches.count()).isZero();
    }

    @Test
    void anUnknownOrNonNumericJobIdIsNotFound() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        baseResume("jane@example.com", "text", "[]");

        mvc.perform(post("/jobs/999999/match").header("Authorization", token)).andExpect(status().isNotFound());
        mvc.perform(post("/jobs/abc/match").header("Authorization", token)).andExpect(status().isNotFound());
    }

    @Test
    void theMatchEndpointsNeedAToken() throws Exception {
        mvc.perform(post("/jobs/1/match")).andExpect(status().isUnauthorized());
        mvc.perform(get("/jobs/1/match")).andExpect(status().isUnauthorized());
    }

    @Test
    void pastingAJobWithABaseResumeReturnsAndStoresTheMatch() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        baseResume("jane@example.com", "Java and Docker engineer", "[\"Java\",\"Docker\"]");

        String json = mvc.perform(post("/jobs/match").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(PASTE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.job.title").value("Backend Engineer"))
                .andExpect(jsonPath("$.match.score").value(67))
                .andExpect(jsonPath("$.match.matchedSkills", hasItem("Java")))
                .andExpect(jsonPath("$.match.missingSkills[0]").value("Kubernetes"))
                .andExpect(jsonPath("$.match.suggestedHeadline").value("Backend Engineer"))
                .andExpect(jsonPath("$.message").value("Matched against your base resume."))
                .andReturn().getResponse().getContentAsString();

        long jobId = ((Number) JsonPath.read(json, "$.job.id")).longValue();
        mvc.perform(get("/jobs/" + jobId + "/match").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(67));
    }

    @Test
    void pastingAJobWithoutAResumeSavesItWithANullMatch() throws Exception {
        String token = tokenWithProfile("jane@example.com");

        mvc.perform(post("/jobs/match").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(PASTE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.job.id").isNumber())
                .andExpect(jsonPath("$.match").value((Object) null))
                .andExpect(jsonPath("$.message").value("Upload a resume to see your match."));
        assertThat(matches.count()).isZero();
    }

    @Test
    void jobsFacetsAndPasteRoutesAreNotCapturedByTheIdRoute() throws Exception {
        String token = tokenWithProfile("jane@example.com");

        mvc.perform(get("/jobs/facets").header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(post("/jobs/match").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(PASTE))
                .andExpect(status().isCreated());
    }

    @Test
    void aJobWithNoRecognisedSkillsReturnsScoreZeroAndANote() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        baseResume("jane@example.com", "Java developer.", "[\"Java\"]");
        long jobId = scrapedJob("Head Chef", "Cook meals for guests every evening.");
        String note = "No known skills were found in this job description, so the score cannot be calculated.";

        mvc.perform(post("/jobs/" + jobId + "/match").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(0))
                .andExpect(jsonPath("$.note").value(note));
        mvc.perform(get("/jobs/" + jobId + "/match").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value(note));
    }

    @Test
    void aJobWithSkillsHasANullNote() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        baseResume("jane@example.com", "Java developer.", "[\"Java\"]");
        long jobId = scrapedJob("Engineer", "We use Java.");

        mvc.perform(post("/jobs/" + jobId + "/match").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value(org.hamcrest.Matchers.nullValue()));
    }
}
