package com.careercopilot.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.careercopilot.profiles.ProfileRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class ReminderIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ReminderService reminders;

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
    private EmailLogRepository emailLogs;

    @Autowired
    private RateLimiter rateLimiter;

    @BeforeEach
    @AfterEach
    void cleanState() {
        applications.deleteAll();
        jobs.deleteAll();
        companies.deleteAll();
        profiles.deleteAll();
        users.deleteAll();
        rateLimiter.reset();
    }

    private record Account(String token, long profileId) {
    }

    private Account account(String email) throws Exception {
        String json = mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret-pass\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + JsonPath.read(json, "$.token");
        mvc.perform(put("/profile").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Jane Doe\"}")).andExpect(status().isOk());
        return new Account(token, profiles.findByUserId(users.findByEmail(email).orElseThrow().getId())
                .orElseThrow().getId());
    }

    private Application application(Account account, String title, String status, Instant submittedAt) {
        Company company = companies.save(new Company("Co " + title));
        Job job = new Job("greenhouse", title, "Description");
        job.setExternalId(title);
        job.setCompanyId(company.getId());
        Application application = new Application(account.profileId(), jobs.save(job).getId());
        application.setStatus(status);
        application.setSubmittedAt(submittedAt);
        return applications.save(application);
    }

    private static Instant daysAgo(int days) {
        return Instant.now().minus(Duration.ofDays(days)).truncatedTo(ChronoUnit.MICROS);
    }

    @Test
    void createsOneReminderOnlyForOldAppliedApplicationsWithoutAReply() throws Exception {
        Account jane = account("jane@example.com");
        Application old = application(jane, "Old", "applied", daysAgo(8));
        application(jane, "Recent", "applied", daysAgo(2));
        application(jane, "Draft", "draft", null);
        application(jane, "Moved on", "interviewing", daysAgo(10));
        Application replied = application(jane, "Replied", "applied", daysAgo(9));
        EmailLog reply = new EmailLog(replied.getId(), EmailLog.TYPE_REPLY, daysAgo(3), EmailLog.STATUS_RECEIVED);
        reply.setClassification("other");
        emailLogs.save(reply);

        assertThat(reminders.createDueReminders()).isEqualTo(1);

        EmailLog reminder = emailLogs.findAll().stream().filter(l -> l.getType().equals("reminder")).findFirst()
                .orElseThrow();
        assertThat(reminder.getApplicationId()).isEqualTo(old.getId());
        assertThat(reminder.getStatus()).isEqualTo("new");
        assertThat(reminder.getOccurredAt()).isEqualTo(old.getSubmittedAt().plus(Duration.ofDays(7)));
        assertThat(reminder.getClassification()).isNull();
    }

    @Test
    void runningAgainNeverCreatesASecondReminderEvenAfterDismissal() throws Exception {
        Account jane = account("jane@example.com");
        application(jane, "Old", "applied", daysAgo(8));

        assertThat(reminders.createDueReminders()).isEqualTo(1);
        assertThat(reminders.createDueReminders()).isZero();

        long id = emailLogs.findAll().get(0).getId();
        mvc.perform(put("/reminders/" + id).header("Authorization", jane.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"dismissed\"}"))
                .andExpect(status().isOk());
        assertThat(reminders.createDueReminders()).isZero();
        assertThat(emailLogs.count()).isEqualTo(1);
    }

    @Test
    void listShowsOnlyTheCallersNewReminders() throws Exception {
        Account jane = account("jane@example.com");
        Account other = account("other@example.com");
        Application mine = application(jane, "Backend", "applied", daysAgo(8));
        application(other, "Frontend", "applied", daysAgo(9));
        reminders.createDueReminders();

        mvc.perform(get("/reminders").header("Authorization", jane.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].applicationId").value(mine.getId()))
                .andExpect(jsonPath("$[0].jobTitle").value("Backend"))
                .andExpect(jsonPath("$[0].company").value("Co Backend"))
                .andExpect(jsonPath("$[0].dueAt").isNotEmpty())
                .andExpect(jsonPath("$[0].id").isNumber());
        mvc.perform(get("/reminders").header("Authorization", other.token()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].jobTitle").value("Frontend"));
    }

    @Test
    void dismissingHidesTheReminderFromTheList() throws Exception {
        Account jane = account("jane@example.com");
        application(jane, "Backend", "applied", daysAgo(8));
        reminders.createDueReminders();
        long id = emailLogs.findAll().get(0).getId();

        mvc.perform(put("/reminders/" + id).header("Authorization", jane.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"dismissed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        mvc.perform(get("/reminders").header("Authorization", jane.token()))
                .andExpect(jsonPath("$.length()").value(0));
        assertThat(emailLogs.findById(id).orElseThrow().getStatus()).isEqualTo("dismissed");
        mvc.perform(get("/applications/" + emailLogs.findById(id).orElseThrow().getApplicationId() + "/emails")
                        .header("Authorization", jane.token()))
                .andExpect(jsonPath("$[0].type").value("reminder"))
                .andExpect(jsonPath("$[0].status").value("dismissed"));
    }

    @Test
    void anotherUsersReminderIsNotFoundAndStaysNew() throws Exception {
        Account owner = account("owner@example.com");
        Account other = account("other@example.com");
        application(owner, "Backend", "applied", daysAgo(8));
        reminders.createDueReminders();
        long id = emailLogs.findAll().get(0).getId();

        mvc.perform(put("/reminders/" + id).header("Authorization", other.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"dismissed\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mvc.perform(put("/reminders/999999").header("Authorization", owner.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"dismissed\"}"))
                .andExpect(status().isNotFound());

        assertThat(emailLogs.findById(id).orElseThrow().getStatus()).isEqualTo("new");
    }

    @Test
    void anEmployerReplyLogCannotBeDismissedAsAReminder() throws Exception {
        Account jane = account("jane@example.com");
        Application app = application(jane, "Backend", "applied", daysAgo(8));
        EmailLog reply = emailLogs.save(new EmailLog(app.getId(), EmailLog.TYPE_REPLY, daysAgo(1),
                EmailLog.STATUS_RECEIVED));

        mvc.perform(put("/reminders/" + reply.getId()).header("Authorization", jane.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"dismissed\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRejectsOtherStatusesAndNeedsAToken() throws Exception {
        Account jane = account("jane@example.com");

        mvc.perform(put("/reminders/1").header("Authorization", jane.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"new\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/reminders/1").header("Authorization", jane.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/reminders")).andExpect(status().isUnauthorized());
        mvc.perform(put("/reminders/1").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"dismissed\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listIsEmptyWithoutAProfile() throws Exception {
        String json = mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"np@example.com\",\"password\":\"secret-pass\"}"))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/reminders").header("Authorization", "Bearer " + JsonPath.read(json, "$.token")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
}
