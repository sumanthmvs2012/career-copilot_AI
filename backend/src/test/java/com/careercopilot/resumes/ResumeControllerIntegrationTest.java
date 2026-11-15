package com.careercopilot.resumes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careercopilot.auth.UserRepository;
import com.careercopilot.common.ratelimit.RateLimiter;
import com.careercopilot.profiles.ProfileRepository;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
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
class ResumeControllerIntegrationTest {

    private static final String PROFILE = "{\"fullName\":\"Jane Doe\"}";

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
    private RateLimiter rateLimiter;

    @BeforeEach
    void cleanState() throws IOException {
        try (Stream<Path> files = Files.walk(storageDir)) {
            files.filter(Files::isRegularFile).forEach(file -> file.toFile().delete());
        }
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

    private static MockMultipartFile pdf(String name, String text) throws IOException {
        return new MockMultipartFile("file", name, "application/pdf", TestFiles.pdf(text));
    }

    private long storedFileCount() throws IOException {
        try (Stream<Path> files = Files.walk(storageDir)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    @Test
    void uploadListAndGetAResume() throws Exception {
        String token = tokenWithProfile("jane@example.com");

        String json = mvc.perform(multipart("/resumes").file(pdf("cv.pdf", "Java developer")).header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.fileName").value("cv.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.isBase").value(true))
                .andExpect(jsonPath("$.textLength").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.parsedText").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(json, "$.id");

        mvc.perform(get("/resumes").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].parsedText").doesNotExist());

        mvc.perform(get("/resumes/" + id).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.parsedText").value(org.hamcrest.Matchers.containsString("Java developer")));
        assertThat(storedFileCount()).isEqualTo(1);
    }

    private int uploadId(String token, String name, String text) throws Exception {
        String json = mvc.perform(multipart("/resumes").file(pdf(name, text)).header("Authorization", token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    @Test
    void uploadingTwiceKeepsBothAndTheNewestIsBase() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        int first = uploadId(token, "one.pdf", "First");
        int second = uploadId(token, "two.pdf", "Second");

        assertThat(second).isNotEqualTo(first);
        assertThat(resumes.count()).isEqualTo(2);
        assertThat(storedFileCount()).isEqualTo(2);
        mvc.perform(get("/resumes").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[0].isBase").value(true))
                .andExpect(jsonPath("$[1].id").value(first))
                .andExpect(jsonPath("$[1].isBase").value(false));
    }

    @Test
    void aThirdUploadIsRefusedWith409() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        uploadId(token, "one.pdf", "First");
        uploadId(token, "two.pdf", "Second");

        mvc.perform(multipart("/resumes").file(pdf("three.pdf", "Third")).header("Authorization", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("You already have 2 resumes. Delete one to upload another."));
        assertThat(resumes.count()).isEqualTo(2);
        assertThat(storedFileCount()).isEqualTo(2);
    }

    @Test
    void deletingTheBasePromotesTheOtherAndRemovesTheFile() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        int first = uploadId(token, "one.pdf", "First");
        int second = uploadId(token, "two.pdf", "Second");

        mvc.perform(delete("/resumes/" + second).header("Authorization", token))
                .andExpect(status().isNoContent());

        mvc.perform(get("/resumes").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(first))
                .andExpect(jsonPath("$[0].isBase").value(true));
        assertThat(storedFileCount()).isEqualTo(1);

        // Room for another upload again.
        uploadId(token, "three.pdf", "Third");
    }

    @Test
    void anotherUserCannotDeleteTheResume() throws Exception {
        String owner = tokenWithProfile("jane@example.com");
        String other = tokenWithProfile("john@example.com");
        int id = uploadId(owner, "cv.pdf", "Private");

        mvc.perform(delete("/resumes/" + id).header("Authorization", other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resume not found."));
        assertThat(resumes.count()).isEqualTo(1);
        assertThat(storedFileCount()).isEqualTo(1);
    }

    @Test
    void aTextFileIsRejected() throws Exception {
        String token = tokenWithProfile("jane@example.com");
        MockMultipartFile txt = new MockMultipartFile("file", "cv.txt", "text/plain", "hello".getBytes());

        mvc.perform(multipart("/resumes").file(txt).header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Upload a PDF or DOCX file."));
        assertThat(resumes.count()).isZero();
    }

    @Test
    void uploadWithoutAProfileReturns404() throws Exception {
        String token = signupToken("jane@example.com");

        mvc.perform(multipart("/resumes").file(pdf("cv.pdf", "Hello")).header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Create your profile first."));
    }

    @Test
    void anotherUserCannotReadTheResume() throws Exception {
        String owner = tokenWithProfile("jane@example.com");
        String other = tokenWithProfile("john@example.com");
        String json = mvc.perform(multipart("/resumes").file(pdf("cv.pdf", "Private")).header("Authorization", owner))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(json, "$.id");

        mvc.perform(get("/resumes/" + id).header("Authorization", other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resume not found."));
        mvc.perform(get("/resumes").header("Authorization", other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void requestsWithoutATokenReturn401() throws Exception {
        mvc.perform(get("/resumes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/resumes/1")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/resumes/1")).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/resumes").file(pdf("cv.pdf", "Hello"))).andExpect(status().isUnauthorized());
    }
}
