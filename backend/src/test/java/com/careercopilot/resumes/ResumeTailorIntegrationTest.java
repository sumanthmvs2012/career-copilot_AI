package com.careercopilot.resumes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import com.careercopilot.matches.JobMatchRepository;
import com.careercopilot.profiles.ProfileRepository;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResumeTailorIntegrationTest {

    private static final String PROFILE = "{\"fullName\":\"Jane Doe\",\"headline\":\"Software Developer\"}";
    private static final String CV = "Jane Doe. Java developer with Docker experience building payments services.";
    private static final String PASTE = "{\"title\":\"Backend Engineer\",\"description\":"
            + "\"We need Java, Docker and Kubernetes experience for our payments platform.\"}";

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.dir", () -> storageDir.toString());
    }

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
    void cleanState() throws IOException {
        try (Stream<Path> files = Files.walk(storageDir)) {
            files.filter(Files::isRegularFile).forEach(file -> file.toFile().delete());
        }
        matches.deleteAll();
        applications.deleteAll();
        List<Resume> copies = resumes.findAll().stream().filter(r -> r.getBaseResumeId() != null).toList();
        resumes.deleteAll(copies);
        resumes.flush();
        resumes.deleteAll();
        jobs.deleteAll();
        companies.deleteAll();
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

    private long upload(String token, String name, String text) throws Exception {
        String json = mvc.perform(multipart("/resumes")
                        .file(new MockMultipartFile("file", name, "application/pdf", TestFiles.pdf(text)))
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
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

    private String tailorJson(String token, long resumeId, long jobId) throws Exception {
        return mvc.perform(post("/resumes/" + resumeId + "/tailor").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"jobId\":" + jobId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private long storedFileCount() throws IOException {
        try (Stream<Path> files = Files.walk(storageDir)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    @Test
    void tailorCreatesACopyAndLeavesTheOriginalUntouched() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        long resumeId = upload(token, "cv.pdf", CV);
        long jobId = scrapedJob("Platform Engineer", "We use Java, Docker and Kubernetes at scale.");
        String originalText = JsonPath.read(mvc.perform(get("/resumes/" + resumeId).header("Authorization", token))
                .andReturn().getResponse().getContentAsString(), "$.parsedText");

        mvc.perform(post("/resumes/" + resumeId + "/tailor").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"jobId\":" + jobId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.fileName").value("Tailored - Platform Engineer - Acme.txt"))
                .andExpect(jsonPath("$.contentType").value("text/plain"))
                .andExpect(jsonPath("$.isBase").value(false))
                .andExpect(jsonPath("$.baseResumeId").value(resumeId))
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.jobTitle").value("Platform Engineer"))
                .andExpect(jsonPath("$.company").value("Acme"))
                .andExpect(jsonPath("$.skills[0]").value("Java"))
                .andExpect(jsonPath("$.parsedText").value(
                        "Platform Engineer\nRelevant skills: Java, Docker\n\n" + originalText))
                .andExpect(jsonPath("$.suggestions[?(@.type=='add_skill')].text")
                        .value("Add Kubernetes to your resume if you have used it."))
                .andExpect(jsonPath("$.suggestions[?(@.type=='headline')]").exists());

        mvc.perform(get("/resumes/" + resumeId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isBase").value(true))
                .andExpect(jsonPath("$.baseResumeId").doesNotExist())
                .andExpect(jsonPath("$.jobId").doesNotExist())
                .andExpect(jsonPath("$.parsedText").value(originalText));
        assertThat(storedFileCount()).isEqualTo(2);
        // Tailoring never writes a match row.
        assertThat(matches.count()).isZero();
    }

    @Test
    void tailoringTheSameJobAgainReplacesThePreviousCopy() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        long resumeId = upload(token, "cv.pdf", CV);
        long jobId = scrapedJob("Platform Engineer", "We use Java and Docker.");

        long first = ((Number) JsonPath.read(tailorJson(token, resumeId, jobId), "$.id")).longValue();
        long second = ((Number) JsonPath.read(tailorJson(token, resumeId, jobId), "$.id")).longValue();

        assertThat(second).isNotEqualTo(first);
        mvc.perform(get("/resumes/" + first).header("Authorization", token)).andExpect(status().isNotFound());
        mvc.perform(get("/resumes").header("Authorization", token))
                .andExpect(jsonPath("$.length()").value(2));
        assertThat(storedFileCount()).isEqualTo(2);
    }

    @Test
    void copiesAreListedWithTheirJobAndUploadsHaveNone() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        long resumeId = upload(token, "cv.pdf", CV);
        long jobId = scrapedJob("Platform Engineer", "We use Java and Docker.");
        long copyId = ((Number) JsonPath.read(tailorJson(token, resumeId, jobId), "$.id")).longValue();

        mvc.perform(get("/resumes").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(resumeId))
                .andExpect(jsonPath("$[0].jobId").doesNotExist())
                .andExpect(jsonPath("$[0].jobTitle").doesNotExist())
                .andExpect(jsonPath("$[1].id").value(copyId))
                .andExpect(jsonPath("$[1].baseResumeId").value(resumeId))
                .andExpect(jsonPath("$[1].jobId").value(jobId))
                .andExpect(jsonPath("$[1].jobTitle").value("Platform Engineer"))
                .andExpect(jsonPath("$[1].company").value("Acme"))
                .andExpect(jsonPath("$[1].parsedText").doesNotExist());
        mvc.perform(get("/resumes/" + copyId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobTitle").value("Platform Engineer"))
                .andExpect(jsonPath("$.company").value("Acme"));
    }

    @Test
    void anotherUsersResumeIsNotFound() throws Exception {
        String owner = tokenWithProfile("jane@example.com");
        String other = tokenWithProfile("other@example.com");
        long resumeId = upload(owner, "cv.pdf", CV);
        long jobId = scrapedJob("Platform Engineer", "Java");

        mvc.perform(post("/resumes/" + resumeId + "/tailor").header("Authorization", other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"jobId\":" + jobId + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resume not found."));
        mvc.perform(post("/resumes/" + resumeId + "/suggestions?jobId=" + jobId).header("Authorization", other))
                .andExpect(status().isNotFound());
        assertThat(resumes.count()).isEqualTo(1);
    }

    @Test
    void anotherUsersPastedJobIsNotFound() throws Exception {
        String owner = tokenWithProfile("jane@example.com");
        String other = tokenWithProfile("other@example.com");
        long pasted = pastedJob(owner);
        long otherResume = upload(other, "cv.pdf", CV);

        mvc.perform(post("/resumes/" + otherResume + "/tailor").header("Authorization", other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"jobId\":" + pasted + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Job not found."));
        mvc.perform(post("/resumes/" + otherResume + "/suggestions?jobId=" + pasted).header("Authorization", other))
                .andExpect(status().isNotFound());
        // The owner can tailor against their own pasted job.
        long ownerResume = upload(owner, "cv.pdf", CV);
        tailorJson(owner, ownerResume, pasted);
    }

    @Test
    void anUnknownJobIsNotFoundAndNoProfileIsNotFound() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        long resumeId = upload(token, "cv.pdf", CV);

        mvc.perform(post("/resumes/" + resumeId + "/tailor").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"jobId\":999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Job not found."));

        String noProfile = signupToken("noprofile@example.com");
        mvc.perform(post("/resumes/" + resumeId + "/tailor").header("Authorization", noProfile)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"jobId\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Create your profile first."));
        mvc.perform(post("/resumes/" + resumeId + "/suggestions?jobId=1").header("Authorization", noProfile))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Create your profile first."));
    }

    @Test
    void tailorNeedsAJobIdAndAToken() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        long resumeId = upload(token, "cv.pdf", CV);

        mvc.perform(post("/resumes/" + resumeId + "/tailor").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/resumes/" + resumeId + "/suggestions").header("Authorization", token))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/resumes/" + resumeId + "/tailor")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"jobId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void suggestionsReturn200ForAnUploadAndForACopyAndNeedAToken() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        long resumeId = upload(token, "cv.pdf", CV);
        long jobId = scrapedJob("Platform Engineer", "We use Java, Docker and Kubernetes at scale.");

        mvc.perform(post("/resumes/" + resumeId + "/suggestions?jobId=" + jobId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].type").value("add_skill"))
                .andExpect(jsonPath("$.suggestions[0].text")
                        .value("Add Kubernetes to your resume if you have used it."));
        assertThat(resumes.count()).isEqualTo(1);
        assertThat(matches.count()).isZero();

        long copyId = ((Number) JsonPath.read(tailorJson(token, resumeId, jobId), "$.id")).longValue();
        mvc.perform(post("/resumes/" + copyId + "/suggestions?jobId=" + jobId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].type").value("add_skill"));

        mvc.perform(post("/resumes/" + resumeId + "/suggestions?jobId=" + jobId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tailoringATailoredCopyIsNotFound() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        long resumeId = upload(token, "cv.pdf", CV);
        long jobId = scrapedJob("Platform Engineer", "Java");
        long copyId = ((Number) JsonPath.read(tailorJson(token, resumeId, jobId), "$.id")).longValue();

        mvc.perform(post("/resumes/" + copyId + "/tailor").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"jobId\":" + jobId + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingAnUploadRemovesItsCopiesAndFiles() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        long resumeId = upload(token, "cv.pdf", CV);
        long job1 = scrapedJob("Platform Engineer", "Java");
        long job2 = scrapedJob("Data Engineer", "Java and Docker");
        tailorJson(token, resumeId, job1);
        tailorJson(token, resumeId, job2);
        assertThat(storedFileCount()).isEqualTo(3);

        mvc.perform(delete("/resumes/" + resumeId).header("Authorization", token))
                .andExpect(status().isNoContent());

        assertThat(resumes.count()).isZero();
        assertThat(storedFileCount()).isZero();
        mvc.perform(get("/resumes").header("Authorization", token)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deletingACopyLeavesTheBaseAndTheCountAlone() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        long first = upload(token, "a.pdf", CV);
        long second = upload(token, "b.pdf", "Jane Doe. Python developer.");
        long jobId = scrapedJob("Platform Engineer", "Java");
        // The older resume is tailored; the newer upload is the base.
        long copyId = ((Number) JsonPath.read(tailorJson(token, first, jobId), "$.id")).longValue();

        mvc.perform(delete("/resumes/" + copyId).header("Authorization", token))
                .andExpect(status().isNoContent());

        mvc.perform(get("/resumes").header("Authorization", token))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[0].isBase").value(true))
                .andExpect(jsonPath("$[1].id").value(first))
                .andExpect(jsonPath("$[1].isBase").value(false));
        assertThat(storedFileCount()).isEqualTo(2);
    }

    @Test
    void copiesDoNotCountTowardTheTwoResumeLimitAndAreNeverTheBaseForMatching() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        long first = upload(token, "a.pdf", CV);
        long jobId = scrapedJob("Platform Engineer", "We use Java, Docker and Kubernetes.");
        tailorJson(token, first, jobId);
        tailorJson(token, first, scrapedJob("Data Engineer", "Java"));
        long second = upload(token, "b.pdf", "Jane Doe. Kubernetes operator.");

        mvc.perform(multipart("/resumes")
                        .file(new MockMultipartFile("file", "c.pdf", "application/pdf", TestFiles.pdf("third")))
                        .header("Authorization", token))
                .andExpect(status().isConflict());

        // The newest upload is the base; no copy is.
        assertThat(resumes.findAll().stream().filter(Resume::isBase).map(Resume::getId)).containsExactly(second);
        mvc.perform(post("/jobs/" + jobId + "/match").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumeId").value(second));
    }
}
