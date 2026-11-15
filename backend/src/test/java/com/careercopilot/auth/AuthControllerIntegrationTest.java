package com.careercopilot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careercopilot.common.ratelimit.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void cleanState() {
        users.deleteAll();
        rateLimiter.reset();
    }

    private static String body(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private MvcResult signup(String email, String password) throws Exception {
        return mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body(email, password)))
                .andReturn();
    }

    @Test
    void signupCreatesTheAccountAndReturnsAValidToken() throws Exception {
        MvcResult result = mvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Jane@Example.com", "secret-pass")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andReturn();

        User saved = users.findByEmail("jane@example.com").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo("secret-pass").startsWith("$2");

        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        Jwt jwt = jwtDecoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(saved.getId()));
        assertThat(result.getResponse().getContentAsString()).doesNotContain("password");
    }

    @Test
    void signupWithAnExistingEmailReturns409() throws Exception {
        signup("jane@example.com", "secret-pass");

        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(body("JANE@example.com", "other-pass")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("An account with this email already exists."));
    }

    @Test
    void signupValidatesTheBodyWithoutEchoingThePassword() throws Exception {
        MvcResult result = mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(body("not-an-email", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").value("Enter a valid email address."))
                .andExpect(jsonPath("$.fieldErrors.password").value("Password must be 8 to 72 characters."))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("short\"");
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed."));
    }

    @Test
    void loginWithTheRightPasswordReturnsAToken() throws Exception {
        signup("jane@example.com", "secret-pass");

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(" Jane@example.com ", "secret-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    void loginWithAWrongPasswordOrUnknownEmailGivesTheSame401() throws Exception {
        signup("jane@example.com", "secret-pass");

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body("jane@example.com", "wrong-pass")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email or password is incorrect."));

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body("nobody@example.com", "secret-pass")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email or password is incorrect."));
    }

    @Test
    void loginIsRateLimited() throws Exception {
        // The test profile allows 5 requests per window; blank bodies fail fast without hashing.
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body("", "")))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body("", "")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many attempts. Please try again shortly."));

        // Signup has its own counter.
        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body("", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void protectedEndpointsRequireAToken() throws Exception {
        mvc.perform(get("/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required."));

        mvc.perform(get("/profile").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aValidTokenIsAcceptedOnProtectedRoutes() throws Exception {
        String token = JsonPath.read(signup("jane@example.com", "secret-pass").getResponse().getContentAsString(),
                "$.token");

        // A fresh user has no profile yet, so an authenticated request gets past security and reaches the 404 handler.
        mvc.perform(get("/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void corsAllowsTheFrontendOrigin() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/auth/login")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void healthCheckIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
