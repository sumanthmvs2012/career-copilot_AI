package com.careercopilot.applications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.careercopilot.auth.User;
import com.careercopilot.jobs.Job;
import com.careercopilot.profiles.Profile;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ApplicationSchemaTest {

    @Autowired
    private EntityManager em;

    @Test
    void newApplicationDefaultsToDraftSubmittedByUser() {
        Application application = persistApplication("a@example.com");
        em.clear();

        Application found = em.find(Application.class, application.getId());
        assertThat(found.getStatus()).isEqualTo("draft");
        assertThat(found.getSubmittedBy()).isEqualTo("user");
        assertThat(found.getResumeId()).isNull();
        assertThat(found.getSubmittedAt()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void secondApplicationForSameProfileAndJobIsRejected() {
        Application first = persistApplication("b@example.com");

        assertThatThrownBy(() -> {
            em.persist(new Application(first.getProfileId(), first.getJobId()));
            em.flush();
        }).isInstanceOf(RuntimeException.class);
    }

    @Test
    void unknownStatusIsRejected() {
        Long profileId = persistProfile("c@example.com");
        Long jobId = persistJob();
        Application application = new Application(profileId, jobId);
        application.setStatus("ghosted");

        assertThatThrownBy(() -> {
            em.persist(application);
            em.flush();
        }).isInstanceOf(RuntimeException.class);
    }

    @Test
    void unknownSubmittedByIsRejected() {
        Long profileId = persistProfile("d@example.com");
        Long jobId = persistJob();
        Application application = new Application(profileId, jobId);
        application.setSubmittedBy("robot");

        assertThatThrownBy(() -> {
            em.persist(application);
            em.flush();
        }).isInstanceOf(RuntimeException.class);
    }

    private Application persistApplication(String email) {
        Application application = new Application(persistProfile(email), persistJob());
        em.persist(application);
        em.flush();
        return application;
    }

    private Long persistProfile(String email) {
        User user = new User(email, "hash");
        em.persist(user);
        em.flush();
        Profile profile = new Profile(user.getId());
        profile.setFullName("Test User");
        em.persist(profile);
        em.flush();
        return profile.getId();
    }

    private Long persistJob() {
        Job job = new Job("manual", "Engineer", "Build things");
        em.persist(job);
        em.flush();
        return job.getId();
    }
}
