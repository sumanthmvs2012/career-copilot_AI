package com.careercopilot.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careercopilot.auth.UserRepository;
import com.careercopilot.common.ratelimit.RateLimiter;
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

/** The default test settings have no Google or key values: the app starts and the feature reports it. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MailNotConfiguredIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private MailProperties properties;

    @BeforeEach
    @AfterEach
    void cleanState() {
        users.deleteAll();
        rateLimiter.reset();
    }

    private String token() throws Exception {
        String json = mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jane@example.com\",\"password\":\"secret-pass\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(json, "$.token");
    }

    @Test
    void theAppStartsWithoutAnyMailSettings() {
        assertThat(properties.configured()).isFalse();
        assertThat(properties.reminderDays()).isEqualTo(7);
        assertThat(properties.toString()).doesNotContain("secret");
    }

    @Test
    void statusReportsNotConfigured() throws Exception {
        mvc.perform(get("/mail-connections").header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.connection").isEmpty());
    }

    @Test
    void startAndSyncReturn503() throws Exception {
        String token = token();

        mvc.perform(post("/mail-connections").header("Authorization", token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("Mailbox connection is not configured."));
        mvc.perform(post("/mail-connections/sync").header("Authorization", token))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void callbackRedirectsToErrorInsteadOfFailing() throws Exception {
        mvc.perform(get("/mail-connections/callback").param("code", "c").param("state", "s"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:5173/dashboard?mail=error"));
    }

    @Test
    void disconnectOfAnUnknownConnectionIs404() throws Exception {
        mvc.perform(delete("/mail-connections/1").header("Authorization", token()))
                .andExpect(status().isNotFound());
    }
}
