package com.careercopilot.jobs.scraper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.careercopilot.jobs.Job;
import com.careercopilot.jobs.JobLocation;
import com.careercopilot.jobs.JobLocationRepository;
import com.careercopilot.jobs.JobRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Real repositories on H2: the reparse fixes bad stored rows, is idempotent and never deletes a job. */
@SpringBootTest
@ActiveProfiles("test")
class JobLocationReparserTest {

    @Autowired
    private JobLocationReparser reparser;

    @Autowired
    private JobRepository jobs;

    @Autowired
    private JobLocationRepository locations;

    @BeforeEach
    @AfterEach
    void clean() {
        locations.deleteAll();
        jobs.deleteAll();
    }

    private Job job(String externalId, String location) {
        Job job = new Job("greenhouse", "Title " + externalId, "Description");
        job.setExternalId(externalId);
        job.setLocation(location);
        return jobs.save(job);
    }

    @Test
    void replacesBadRowsKeepsGoodOnesAndIsIdempotent() {
        Job bad = job("bad", "Seattle, Atlanta, United States");
        locations.save(new JobLocation(bad.getId(), "Seattle", "Atlanta", "United States"));
        Job good = job("good", "Berlin, Germany");
        JobLocation goodRow = locations.save(new JobLocation(good.getId(), "Berlin", null, "Germany"));
        Job missing = job("missing", "NYC");
        Job noText = job("none", null);

        assertThat(reparser.reparseAll()).isEqualTo(2);

        assertThat(locations.findByJobIdIn(List.of(bad.getId())))
                .extracting(JobLocation::getCity, JobLocation::getState, JobLocation::getCountry)
                .containsExactlyInAnyOrder(tuple("Seattle", null, null), tuple("Atlanta", null, "United States"));
        assertThat(locations.findByJobIdIn(List.of(missing.getId())))
                .extracting(JobLocation::getCity).containsExactly("New York");
        assertThat(locations.findByJobIdIn(List.of(noText.getId()))).isEmpty();
        assertThat(locations.findById(goodRow.getId())).isPresent();
        assertThat(jobs.count()).isEqualTo(4);

        assertThat(reparser.reparseAll()).isZero();
        assertThat(jobs.count()).isEqualTo(4);
    }
}
