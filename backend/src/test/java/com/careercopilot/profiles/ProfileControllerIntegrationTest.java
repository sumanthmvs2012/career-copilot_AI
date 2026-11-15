package com.careercopilot.profiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careercopilot.auth.UserRepository;
import com.careercopilot.common.ratelimit.RateLimiter;
import com.jayway.jsonpath.JsonPath;
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
class ProfileControllerIntegrationTest {

    private static final String VALID = "{\"fullName\":\"Jane Doe\",\"headline\":\"Engineer\","
            + "\"location\":\"Berlin\",\"remoteOk\":true,\"autoSubmitEnabled\":false}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private ProfileRepository profiles;

    @Autowired
    private RateLimiter rateLimiter;

    @BeforeEach
    void cleanState() {
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

    @Test
    void getWithoutATokenReturns401() throws Exception {
        mvc.perform(get("/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void getBeforeAnyPutReturns404() throws Exception {
        String token = signupToken("jane@example.com");

        mvc.perform(get("/profile").header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Create your profile first."));
    }

    @Test
    void putCreatesTheProfileAndGetReturnsIt() throws Exception {
        String token = signupToken("jane@example.com");

        mvc.perform(put("/profile").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.fullName").value("Jane Doe"))
                .andExpect(jsonPath("$.headline").value("Engineer"))
                .andExpect(jsonPath("$.location").value("Berlin"))
                .andExpect(jsonPath("$.remoteOk").value(true))
                .andExpect(jsonPath("$.autoSubmitEnabled").value(false))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString());

        assertThat(profiles.count()).isEqualTo(1);

        mvc.perform(get("/profile").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Jane Doe"));
    }

    @Test
    void putAgainUpdatesTheSameRow() throws Exception {
        String token = signupToken("jane@example.com");
        mvc.perform(put("/profile").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(VALID)).andExpect(status().isOk());

        mvc.perform(put("/profile").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Jane Smith\",\"headline\":\"  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Jane Smith"))
                .andExpect(jsonPath("$.headline").doesNotExist())
                .andExpect(jsonPath("$.remoteOk").value(false));

        assertThat(profiles.count()).isEqualTo(1);
    }

    @Test
    void blankFullNameReturns400WithAFieldError() throws Exception {
        String token = signupToken("jane@example.com");

        mvc.perform(put("/profile").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fullName").isString());
        assertThat(profiles.count()).isZero();
    }

    @Test
    void tooLongFullNameReturns400() throws Exception {
        String token = signupToken("jane@example.com");

        mvc.perform(put("/profile").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"" + "a".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fullName").isString());
    }

    @Test
    void anotherUserCannotSeeTheProfile() throws Exception {
        String first = signupToken("jane@example.com");
        String second = signupToken("john@example.com");
        mvc.perform(put("/profile").header("Authorization", first)
                .contentType(MediaType.APPLICATION_JSON).content(VALID)).andExpect(status().isOk());

        mvc.perform(get("/profile").header("Authorization", second))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Create your profile first."));
    }
}
