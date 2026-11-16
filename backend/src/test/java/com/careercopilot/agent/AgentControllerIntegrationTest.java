package com.careercopilot.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careercopilot.applications.Application;
import com.careercopilot.applications.ApplicationRepository;
import com.careercopilot.auth.UserRepository;
import com.careercopilot.common.ratelimit.RateLimiter;
import com.careercopilot.jobs.Company;
import com.careercopilot.jobs.CompanyRepository;
import com.careercopilot.jobs.Job;
import com.careercopilot.jobs.JobRepository;
import com.careercopilot.matches.JobMatchRepository;
import com.careercopilot.profiles.Profile;
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
class AgentControllerIntegrationTest {

    private static final String PROFILE = "{\"fullName\":\"Jane Doe\",\"location\":\"Berlin, Germany\","
            + "\"remoteOk\":true}";

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

    private Profile profileOf(String email) {
        return profiles.findAll().stream()
                .filter(p -> users.findById(p.getUserId()).orElseThrow().getEmail().equals(email))
                .findFirst().orElseThrow();
    }

    private Resume baseResume(String email, String skillsJson) {
        Resume resume = new Resume(profileOf(email).getId(), "resumes/x.pdf", "cv.pdf", "application/pdf",
                "Experienced developer.");
        resume.setSkills(skillsJson);
        return resumes.save(resume);
    }

    private long scrapedJob(String title, String description) {
        Company company = companies.save(new Company("Acme"));
        Job job = new Job("greenhouse", title, description);
        job.setExternalId(title);
        job.setCompanyId(company.getId());
        return jobs.save(job).getId();
    }

    @Test
    void createsDraftsOnlyForJobsAtOrAboveTheThreshold() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        Resume resume = baseResume("jane@example.com", "[\"Java\",\"Docker\"]");
        long good = scrapedJob("Java Engineer", "We use Java and Docker daily.");
        long weak = scrapedJob("Platform Engineer", "We use Java, Docker and Kubernetes.");

        mvc.perform(post("/agent/run").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.considered").value(2))
                .andExpect(jsonPath("$.draftsCreated").value(1));

        long profileId = profileOf("jane@example.com").getId();
        List<Application> rows = applications.findByProfileIdOrderByCreatedAtDescIdDesc(profileId);
        assertThat(rows).hasSize(1);
        Application draft = rows.get(0);
        assertThat(draft.getJobId()).isEqualTo(good);
        assertThat(applications.existsByProfileIdAndJobId(profileId, weak)).isFalse();
        assertThat(draft.getResumeId()).isEqualTo(resume.getId());
        assertThat(draft.getCoverLetter()).contains("Java Engineer", "Acme", "Java and Docker", "Jane Doe");
    }

    @Test
    void neverSubmitsEvenWhenAutoSubmitIsOn() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        Profile profile = profileOf("jane@example.com");
        profile.setAutoSubmitEnabled(true);
        profiles.save(profile);
        baseResume("jane@example.com", "[\"Java\"]");
        scrapedJob("Java Engineer", "Java needed.");

        mvc.perform(post("/agent/run").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draftsCreated").value(1));

        Application draft = applications.findAll().get(0);
        assertThat(draft.getStatus()).isEqualTo("draft");
        assertThat(draft.getSubmittedAt()).isNull();
        assertThat(draft.getSubmittedBy()).isEqualTo("user");
    }

    @Test
    void aSecondRunCreatesNothingNew() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        baseResume("jane@example.com", "[\"Java\"]");
        scrapedJob("Java Engineer", "Java needed.");

        mvc.perform(post("/agent/run").header("Authorization", token))
                .andExpect(jsonPath("$.draftsCreated").value(1));
        mvc.perform(post("/agent/run").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.considered").value(0))
                .andExpect(jsonPath("$.draftsCreated").value(0));

        assertThat(applications.count()).isEqualTo(1);
    }

    @Test
    void skipsJobsTheUserAlreadyTracks() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        baseResume("jane@example.com", "[\"Java\"]");
        long jobId = scrapedJob("Java Engineer", "Java needed.");
        applications.save(new Application(profileOf("jane@example.com").getId(), jobId));

        mvc.perform(post("/agent/run").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.considered").value(0))
                .andExpect(jsonPath("$.draftsCreated").value(0));

        assertThat(applications.count()).isEqualTo(1);
    }

    @Test
    void neverTouchesAnotherUsersDataOrPastedJobs() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        tokenWithProfile("bob@example.com");
        baseResume("jane@example.com", "[\"Java\"]");
        baseResume("bob@example.com", "[\"Java\"]");
        Job pasted = new Job("manual", "Java Engineer", "Java needed.");
        pasted.setCreatedByProfileId(profileOf("bob@example.com").getId());
        jobs.save(pasted);

        mvc.perform(post("/agent/run").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.considered").value(0))
                .andExpect(jsonPath("$.draftsCreated").value(0));

        scrapedJob("Java Developer", "Java needed.");
        // Jane's first run moved only her own marker, so Bob still has a first-run window of his own.
        assertThat(profileOf("bob@example.com").getLastAgentRunAt()).isNull();
        mvc.perform(post("/agent/run").header("Authorization", token))
                .andExpect(jsonPath("$.draftsCreated").value(1));

        long bobId = profileOf("bob@example.com").getId();
        assertThat(applications.findByProfileIdOrderByCreatedAtDescIdDesc(bobId)).isEmpty();
        assertThat(applications.count()).isEqualTo(1);
    }

    @Test
    void returns404WithoutAProfile() throws Exception {
        String token = signupToken("jane@example.com");

        mvc.perform(post("/agent/run").header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void returns409WithoutABaseResume() throws Exception {
        String token = tokenWithProfile("jane@example.com");

        mvc.perform(post("/agent/run").header("Authorization", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Upload a resume first."));
    }

    @Test
    void requiresAToken() throws Exception {
        mvc.perform(post("/agent/run")).andExpect(status().isUnauthorized());
    }
}
