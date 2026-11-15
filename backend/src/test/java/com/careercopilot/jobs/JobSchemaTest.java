package com.careercopilot.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JobSchemaTest {

    @Autowired
    private EntityManager em;

    @Test
    void manualJobsWithNullExternalIdCanRepeat() {
        em.persist(new Job("manual", "Engineer", "Build things"));
        em.persist(new Job("manual", "Engineer", "Build things"));
        em.flush();

        Job job = new Job("manual", "Designer", "Design things");
        em.persist(job);
        em.flush();

        assertThat(job.getId()).isNotNull();
        assertThat(job.getScrapedAt()).isNotNull();
        assertThat(job.getCreatedAt()).isNotNull();
    }

    @Test
    void sameSourceAndExternalIdIsRejected() {
        Job first = new Job("greenhouse", "Engineer", "Build things");
        first.setExternalId("123");
        em.persist(first);
        em.flush();

        Job second = new Job("greenhouse", "Engineer again", "Build more things");
        second.setExternalId("123");

        assertThatThrownBy(() -> {
            em.persist(second);
            em.flush();
        }).isInstanceOf(RuntimeException.class);
    }

    @Test
    void categoryIsStoredAndNullByDefault() {
        Job plain = new Job("manual", "Engineer", "Build things");
        Job tagged = new Job("manual", "Designer", "Design things");
        tagged.setCategory("Design");
        em.persist(plain);
        em.persist(tagged);
        em.flush();
        em.clear();

        assertThat(em.find(Job.class, plain.getId()).getCategory()).isNull();
        assertThat(em.find(Job.class, tagged.getId()).getCategory()).isEqualTo("Design");
    }

    @Test
    void locationWithoutCityStateAndCountryIsRejected() {
        Job job = persistJob("Engineer");

        assertThatThrownBy(() -> {
            em.persist(new JobLocation(job.getId(), null, null, null));
            em.flush();
        }).isInstanceOf(RuntimeException.class);
    }

    @Test
    void locationWithOnlyCountryIsAccepted() {
        Job job = persistJob("Engineer");
        JobLocation location = new JobLocation(job.getId(), null, null, "Germany");
        em.persist(location);
        em.flush();

        assertThat(location.getId()).isNotNull();
    }

    @Test
    void deletingAJobDeletesItsLocations() {
        Job job = persistJob("Engineer");
        em.persist(new JobLocation(job.getId(), "Austin", "Texas", "United States"));
        em.persist(new JobLocation(job.getId(), "Berlin", null, "Germany"));
        em.flush();

        em.createNativeQuery("DELETE FROM jobs WHERE id = :id").setParameter("id", job.getId()).executeUpdate();
        em.clear();

        assertThat(countLocations("SELECT l FROM JobLocation l WHERE l.jobId = " + job.getId())).isZero();
    }

    @Test
    void locationsCanBeQueriedByCountryStateAndCity() {
        Job job = persistJob("Engineer");
        em.persist(new JobLocation(job.getId(), "Austin", "Texas", "United States"));
        em.persist(new JobLocation(job.getId(), "Berlin", null, "Germany"));
        em.flush();

        assertThat(countLocations("SELECT l FROM JobLocation l WHERE l.country = 'Germany'")).isEqualTo(1);
        assertThat(countLocations("SELECT l FROM JobLocation l WHERE l.country = 'United States' AND l.state = 'Texas'"))
                .isEqualTo(1);
        assertThat(countLocations("SELECT l FROM JobLocation l WHERE LOWER(l.city) = 'austin'")).isEqualTo(1);
        assertThat(countLocations("SELECT l FROM JobLocation l WHERE l.country = 'France'")).isZero();
    }

    private Job persistJob(String title) {
        Job job = new Job("manual", title, "Build things");
        em.persist(job);
        em.flush();
        return job;
    }

    private int countLocations(String jpql) {
        return em.createQuery(jpql, JobLocation.class).getResultList().size();
    }

    @Test
    void companyAllowsNullAtsType() {
        Company company = new Company("Acme");
        em.persist(company);
        em.flush();

        assertThat(company.getId()).isNotNull();
        assertThat(company.getAtsType()).isNull();
        assertThat(company.getCreatedAt()).isNotNull();
    }
}
