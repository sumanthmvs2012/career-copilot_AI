package com.careercopilot.matches;

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
class JobMatchSchemaTest {

    @Autowired
    private EntityManager em;

    @Test
    void newMatchDefaultsToUnknownLocationFitWithTimestamps() {
        JobMatch match = persistMatch("a@example.com", 70);
        em.clear();

        JobMatch found = em.find(JobMatch.class, match.getId());
        assertThat(found.getScore()).isEqualTo(70);
        assertThat(found.getLocationFitStatus()).isEqualTo("unknown");
        assertThat(found.getResumeId()).isNull();
        assertThat(found.getMatchedSkills()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void jsonTextColumnsRoundTrip() {
        JobMatch match = new JobMatch(persistProfile("b@example.com"), persistJob());
        match.setScore(50);
        match.setMatchedSkills("[\"Java\",\"C++\"]");
        match.setMissingSkills("[\"Docker\"]");
        em.persist(match);
        em.flush();
        em.clear();

        JobMatch found = em.find(JobMatch.class, match.getId());
        assertThat(found.getMatchedSkills()).isEqualTo("[\"Java\",\"C++\"]");
        assertThat(found.getMissingSkills()).isEqualTo("[\"Docker\"]");
    }

    @Test
    void secondMatchForSameProfileAndJobIsRejected() {
        JobMatch first = persistMatch("c@example.com", 10);

        assertThatThrownBy(() -> {
            em.persist(new JobMatch(first.getProfileId(), first.getJobId()));
            em.flush();
        }).isInstanceOf(RuntimeException.class);
    }

    @Test
    void scoreOutsideZeroToHundredIsRejected() {
        assertThatThrownBy(() -> persistMatch("d@example.com", 101)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void unknownLocationFitStatusIsRejected() {
        JobMatch match = new JobMatch(persistProfile("e@example.com"), persistJob());
        match.setLocationFitStatus("maybe");

        assertThatThrownBy(() -> {
            em.persist(match);
            em.flush();
        }).isInstanceOf(RuntimeException.class);
    }

    private JobMatch persistMatch(String email, int score) {
        JobMatch match = new JobMatch(persistProfile(email), persistJob());
        match.setScore(score);
        em.persist(match);
        em.flush();
        return match;
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
