package com.careercopilot.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import com.careercopilot.mail.MailProvider.MailMessage;
import com.careercopilot.profiles.ProfileRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Mailbox endpoints with the settings present and a fake provider, so nothing leaves the JVM. */
@SpringBootTest(properties = {
        "app.mail.google-client-id=test-client",
        "app.mail.google-client-secret=test-secret",
        "app.mail.google-redirect-uri=http://localhost:8080/mail-connections/callback",
        "app.mail.token-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.mail.frontend-url=http://localhost:5173/"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MailConnectionIntegrationTest.FakeProviderConfig.class)
class MailConnectionIntegrationTest {

    @TestConfiguration
    static class FakeProviderConfig {

        @Bean
        @Primary
        FakeMailProvider fakeMailProvider() {
            return new FakeMailProvider();
        }
    }

    private static final Instant SUBMITTED = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant AFTER = Instant.parse("2026-11-05T10:00:00Z");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FakeMailProvider fake;

    @Autowired
    private UserRepository users;

    @Autowired
    private ProfileRepository profiles;

    @Autowired
    private JobRepository jobs;

    @Autowired
    private CompanyRepository companies;

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private MailConnectionRepository connections;

    @Autowired
    private EmailLogRepository emailLogs;

    @Autowired
    private MailStateService states;

    @Autowired
    private TokenCipher cipher;

    @Autowired
    private RateLimiter rateLimiter;

    @BeforeEach
    @AfterEach
    void cleanState() {
        fake.reset();
        connections.deleteAll();
        applications.deleteAll();
        jobs.deleteAll();
        companies.deleteAll();
        profiles.deleteAll();
        users.deleteAll();
        rateLimiter.reset();
    }

    private record Account(String token, long userId, long profileId) {
    }

    private Account account(String email) throws Exception {
        String json = mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret-pass\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + JsonPath.read(json, "$.token");
        mvc.perform(put("/profile").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Jane Doe\"}")).andExpect(status().isOk());
        long userId = users.findByEmail(email).orElseThrow().getId();
        return new Account(token, userId, profiles.findByUserId(userId).orElseThrow().getId());
    }

    private long appliedApplication(Account account, String company, String title) {
        Company saved = companies.save(new Company(company));
        Job job = new Job("greenhouse", title, "Description");
        job.setExternalId(title + company + account.profileId());
        job.setCompanyId(saved.getId());
        long jobId = jobs.save(job).getId();
        Application application = new Application(account.profileId(), jobId);
        application.setStatus("applied");
        application.setSubmittedAt(SUBMITTED);
        return applications.save(application).getId();
    }

    private MailConnection connect(Account account) {
        return connections.save(new MailConnection(account.userId(), cipher.encrypt(FakeMailProvider.REFRESH_TOKEN),
                GmailMailProvider.SCOPE));
    }

    private static MailMessage message(String id, String from, String subject, Instant at, String snippet) {
        return new MailMessage(id, from, subject, at, snippet);
    }

    // --- status, start, callback ---

    @Test
    void statusIsConfiguredWithNoConnectionAtFirst() throws Exception {
        Account jane = account("jane@example.com");

        mvc.perform(get("/mail-connections").header("Authorization", jane.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.connection").isEmpty());
    }

    @Test
    void startReturnsAnAuthUrlCarryingASignedStateForTheCaller() throws Exception {
        Account jane = account("jane@example.com");

        String json = mvc.perform(post("/mail-connections").header("Authorization", jane.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        String authUrl = JsonPath.read(json, "$.authUrl");
        String state = authUrl.substring(authUrl.indexOf("state=") + 6);
        assertThat(states.verify(state)).isEqualTo(jane.userId());
    }

    @Test
    void startReturns409WhenAlreadyConnected() throws Exception {
        Account jane = account("jane@example.com");
        connect(jane);

        mvc.perform(post("/mail-connections").header("Authorization", jane.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void callbackStoresTheTokenEncryptedAndRedirectsToTheDashboardWithoutAnAuthorizationHeader() throws Exception {
        Account jane = account("jane@example.com");
        String state = states.create(jane.userId());

        mvc.perform(get("/mail-connections/callback").param("code", "good-code").param("state", state))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:5173/dashboard?mail=connected"));

        assertThat(fake.exchangedCodes).containsExactly("good-code");
        MailConnection stored = connections.findAll().get(0);
        assertThat(stored.getUserId()).isEqualTo(jane.userId());
        assertThat(stored.getEncryptedRefreshToken()).isNotBlank()
                .doesNotContain(FakeMailProvider.REFRESH_TOKEN);
        assertThat(cipher.decrypt(stored.getEncryptedRefreshToken())).isEqualTo(FakeMailProvider.REFRESH_TOKEN);
        assertThat(stored.getRevokedAt()).isNull();

        mvc.perform(get("/mail-connections").header("Authorization", jane.token()))
                .andExpect(jsonPath("$.connection.id").value(stored.getId()))
                .andExpect(jsonPath("$.connection.provider").value("gmail"))
                .andExpect(jsonPath("$.connection.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.connection.encryptedRefreshToken").doesNotExist());
    }

    @Test
    void callbackTwiceKeepsASingleActiveConnection() throws Exception {
        Account jane = account("jane@example.com");
        String state = states.create(jane.userId());

        mvc.perform(get("/mail-connections/callback").param("code", "c1").param("state", state))
                .andExpect(status().isFound());
        mvc.perform(get("/mail-connections/callback").param("code", "c2").param("state", state))
                .andExpect(status().isFound());

        assertThat(connections.count()).isEqualTo(1);
    }

    @Test
    void callbackRejectsATamperedStateAndNeverCallsTheProvider() throws Exception {
        Account jane = account("jane@example.com");
        String state = states.create(jane.userId());
        String tampered = state.substring(0, state.length() - 3) + "abc";

        mvc.perform(get("/mail-connections/callback").param("code", "good-code").param("state", tampered))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:5173/dashboard?mail=error"));
        mvc.perform(get("/mail-connections/callback").param("code", "good-code").param("state", "garbage"))
                .andExpect(header().string("Location", "http://localhost:5173/dashboard?mail=error"));

        assertThat(fake.exchangedCodes).isEmpty();
        assertThat(connections.count()).isZero();
    }

    @Test
    void aLoginTokenIsNotAcceptedAsTheState() throws Exception {
        Account jane = account("jane@example.com");
        String loginToken = jane.token().substring("Bearer ".length());

        mvc.perform(get("/mail-connections/callback").param("code", "good-code").param("state", loginToken))
                .andExpect(header().string("Location", "http://localhost:5173/dashboard?mail=error"));

        assertThat(fake.exchangedCodes).isEmpty();
        assertThat(connections.count()).isZero();
    }

    @Test
    void aStateCannotBeUsedAsALoginToken() throws Exception {
        Account jane = account("jane@example.com");
        String state = states.create(jane.userId());

        mvc.perform(get("/applications").header("Authorization", "Bearer " + state))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/mail-connections").header("Authorization", "Bearer " + state))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void callbackWithMissingParametersGoogleErrorsOrABadCodeRedirectsToError() throws Exception {
        Account jane = account("jane@example.com");
        String state = states.create(jane.userId());
        String error = "http://localhost:5173/dashboard?mail=error";

        mvc.perform(get("/mail-connections/callback")).andExpect(status().isFound())
                .andExpect(header().string("Location", error));
        mvc.perform(get("/mail-connections/callback").param("state", state))
                .andExpect(header().string("Location", error));
        mvc.perform(get("/mail-connections/callback").param("error", "access_denied").param("state", state))
                .andExpect(header().string("Location", error));
        mvc.perform(get("/mail-connections/callback").param("code", "bad-code").param("state", state))
                .andExpect(header().string("Location", error));

        assertThat(connections.count()).isZero();
    }

    @Test
    void callbackWithoutARefreshTokenFromGoogleIsAnError() throws Exception {
        Account jane = account("jane@example.com");
        fake.grantedRefreshToken = null;

        mvc.perform(get("/mail-connections/callback").param("code", "c").param("state", states.create(jane.userId())))
                .andExpect(header().string("Location", "http://localhost:5173/dashboard?mail=error"));

        assertThat(connections.count()).isZero();
    }

    @Test
    void mailEndpointsNeedAToken() throws Exception {
        mvc.perform(get("/mail-connections")).andExpect(status().isUnauthorized());
        mvc.perform(post("/mail-connections")).andExpect(status().isUnauthorized());
        mvc.perform(post("/mail-connections/sync")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/mail-connections/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/applications/1/emails")).andExpect(status().isUnauthorized());
    }

    // --- disconnect ---

    @Test
    void disconnectRevokesAtTheProviderClearsTheTokenAndHidesTheConnection() throws Exception {
        Account jane = account("jane@example.com");
        MailConnection connection = connect(jane);

        mvc.perform(delete("/mail-connections/" + connection.getId()).header("Authorization", jane.token()))
                .andExpect(status().isNoContent());

        assertThat(fake.revokedTokens).containsExactly(FakeMailProvider.REFRESH_TOKEN);
        MailConnection stored = connections.findById(connection.getId()).orElseThrow();
        assertThat(stored.getEncryptedRefreshToken()).isNull();
        assertThat(stored.getRevokedAt()).isNotNull();
        mvc.perform(get("/mail-connections").header("Authorization", jane.token()))
                .andExpect(jsonPath("$.connection").isEmpty());
        mvc.perform(delete("/mail-connections/" + connection.getId()).header("Authorization", jane.token()))
                .andExpect(status().isNotFound());
        // After a revoke the user can connect again.
        mvc.perform(post("/mail-connections").header("Authorization", jane.token())).andExpect(status().isOk());
    }

    @Test
    void disconnectStillClearsTheTokenWhenTheProviderFails() throws Exception {
        Account jane = account("jane@example.com");
        MailConnection connection = connect(jane);
        fake.failRevoke = true;

        mvc.perform(delete("/mail-connections/" + connection.getId()).header("Authorization", jane.token()))
                .andExpect(status().isNoContent());

        MailConnection stored = connections.findById(connection.getId()).orElseThrow();
        assertThat(stored.getEncryptedRefreshToken()).isNull();
        assertThat(stored.getRevokedAt()).isNotNull();
    }

    @Test
    void disconnectIsNotFoundForAnotherUsersConnectionAndLeavesItIntact() throws Exception {
        Account owner = account("owner@example.com");
        Account other = account("other@example.com");
        MailConnection connection = connect(owner);

        mvc.perform(delete("/mail-connections/" + connection.getId()).header("Authorization", other.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mvc.perform(delete("/mail-connections/999999").header("Authorization", other.token()))
                .andExpect(status().isNotFound());

        assertThat(connections.findById(connection.getId()).orElseThrow().getEncryptedRefreshToken()).isNotNull();
        assertThat(fake.revokedTokens).isEmpty();
    }

    // --- sync ---

    @Test
    void syncStoresClassifiedRepliesOnlyForTheMatchingApplicationAndDedupesByMessageId() throws Exception {
        Account jane = account("jane@example.com");
        long acme = appliedApplication(jane, "Acme", "Backend Engineer");
        long globex = appliedApplication(jane, "Globex", "Data Engineer");
        connect(jane);
        fake.inbox.add(message("g-1", "Acme Recruiting <jobs@acme.com>", "Interview invitation", AFTER,
                "Please share your availability, SECRET-BODY-TEXT"));
        fake.inbox.add(message("g-2", "Globex HR <hr@globex.com>", "Your application", AFTER.plusSeconds(60),
                "Unfortunately we will not be moving forward"));
        fake.inbox.add(message("g-3", "News <news@other.com>", "Weekly digest", AFTER, "nothing"));
        fake.inbox.add(message("g-4", "jobs@acme.com", "Old mail", Instant.parse("2026-10-01T00:00:00Z"), ""));

        mvc.perform(post("/mail-connections/sync").header("Authorization", jane.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checked").value(3))
                .andExpect(jsonPath("$.newReplies").value(2));
        // The scan starts from the earliest submission and used the decrypted token.
        assertThat(fake.lastSince).isEqualTo(SUBMITTED);
        assertThat(fake.lastRefreshToken).isEqualTo(FakeMailProvider.REFRESH_TOKEN);

        mvc.perform(post("/mail-connections/sync").header("Authorization", jane.token()))
                .andExpect(jsonPath("$.checked").value(3))
                .andExpect(jsonPath("$.newReplies").value(0));

        assertThat(emailLogs.count()).isEqualTo(2);
        mvc.perform(get("/applications/" + acme + "/emails").header("Authorization", jane.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("employer_reply"))
                .andExpect(jsonPath("$[0].classification").value("interview"))
                .andExpect(jsonPath("$[0].subject").value("Interview invitation"))
                .andExpect(jsonPath("$[0].status").value("received"))
                .andExpect(jsonPath("$[0].occurredAt").value("2026-11-05T10:00:00Z"));
        mvc.perform(get("/applications/" + globex + "/emails").header("Authorization", jane.token()))
                .andExpect(jsonPath("$[0].classification").value("rejection"));
        // Neither the sender nor the snippet is stored anywhere in the log rows.
        emailLogs.findAll().forEach(row -> {
            assertThat(String.valueOf(row.getSubject())).doesNotContain("SECRET-BODY-TEXT");
            assertThat(row.getExternalMessageId()).startsWith("g-");
        });
        assertThat(connections.findAll().get(0).getLastSyncedAt()).isNotNull();

        mvc.perform(get("/applications").header("Authorization", jane.token()))
                .andExpect(jsonPath("$[?(@.id==" + acme + ")].emailSent").value(true))
                .andExpect(jsonPath("$[?(@.id==" + acme + ")].emailClassification").value("interview"));
    }

    @Test
    void syncWithoutAConnectionReturns409AndWithoutApplicationsChecksNothing() throws Exception {
        Account jane = account("jane@example.com");

        mvc.perform(post("/mail-connections/sync").header("Authorization", jane.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Connect your mailbox first."));

        connect(jane);
        mvc.perform(post("/mail-connections/sync").header("Authorization", jane.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checked").value(0))
                .andExpect(jsonPath("$.newReplies").value(0));
    }

    @Test
    void syncOnlyEverReadsTheCallersOwnApplications() throws Exception {
        Account jane = account("jane@example.com");
        Account other = account("other@example.com");
        appliedApplication(jane, "Acme", "Backend Engineer");
        long othersApplication = appliedApplication(other, "Acme", "Backend Engineer");
        connect(jane);
        fake.inbox.add(message("g-1", "jobs@acme.com", "Hello", AFTER, ""));

        mvc.perform(post("/mail-connections/sync").header("Authorization", jane.token()))
                .andExpect(jsonPath("$.newReplies").value(1));

        mvc.perform(get("/applications/" + othersApplication + "/emails").header("Authorization", other.token()))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/applications/" + othersApplication + "/emails").header("Authorization", jane.token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void aProviderFailureDuringSyncIsA502WithAFixedMessage() throws Exception {
        Account jane = account("jane@example.com");
        appliedApplication(jane, "Acme", "Backend Engineer");
        connect(jane);
        fake.failListing = true;

        mvc.perform(post("/mail-connections/sync").header("Authorization", jane.token()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message").value("Could not reach your mailbox. Try again later."));
    }

    @Test
    void emailsAreNotFoundForAnotherUsersOrUnknownApplication() throws Exception {
        Account owner = account("owner@example.com");
        Account other = account("other@example.com");
        long id = appliedApplication(owner, "Acme", "Backend Engineer");

        mvc.perform(get("/applications/" + id + "/emails").header("Authorization", other.token()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/applications/999999/emails").header("Authorization", owner.token()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/applications/" + id + "/emails").header("Authorization", owner.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
}
