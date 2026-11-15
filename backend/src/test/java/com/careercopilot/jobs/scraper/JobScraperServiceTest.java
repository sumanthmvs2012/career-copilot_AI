package com.careercopilot.jobs.scraper;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.auth.UserRepository;
import com.careercopilot.jobs.Company;
import com.careercopilot.jobs.CompanyRepository;
import com.careercopilot.jobs.Job;
import com.careercopilot.jobs.JobLocation;
import com.careercopilot.jobs.JobLocationRepository;
import com.careercopilot.jobs.JobRepository;
import com.careercopilot.profiles.ProfileRepository;
import com.careercopilot.resumes.ResumeRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpServerErrorException;

/** Runs the real repositories on H2 with fake sources, so nothing touches the network. */
@SpringBootTest
@ActiveProfiles("test")
class JobScraperServiceTest {

    @Autowired
    private JobRepository jobs;

    @Autowired
    private JobLocationRepository locations;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private CompanyRepository companies;

    @Autowired
    private ProfileRepository profiles;

    @Autowired
    private UserRepository users;

    @Autowired
    private ResumeRepository resumes;

    @Autowired
    private ApplicationContext context;

    private final List<ScrapedJob> greenhouseJobs = new ArrayList<>();
    private boolean leverFails;

    private final JobSource greenhouse = new JobSource() {
        @Override
        public String source() {
            return "greenhouse";
        }

        @Override
        public List<ScrapedJob> fetch(String boardToken) {
            return greenhouseJobs;
        }
    };

    private final JobSource lever = new JobSource() {
        @Override
        public String source() {
            return "lever";
        }

        @Override
        public List<ScrapedJob> fetch(String boardToken) {
            if (leverFails) {
                throw HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "Bad Gateway", null, null, null);
            }
            return List.of(scraped("L1", "Lever role", "desc", null, null));
        }
    };

    @BeforeEach
    void clean() {
        locations.deleteAll();
        jobs.deleteAll();
        companies.deleteAll();
        resumes.deleteAll();
        profiles.deleteAll();
        users.deleteAll();
        greenhouseJobs.clear();
        leverFails = false;
    }

    private JobScraperService service(String boards) {
        ScraperProperties properties =
                new ScraperProperties(true, "0 0 * * * *", boards, Duration.ofSeconds(1), Duration.ofSeconds(1));
        return new JobScraperService(properties, List.of(greenhouse, lever), companies, jobs, locations, transactions);
    }

    private static ScrapedJob scraped(String id, String title, String description, Instant postedAt, String company) {
        return new ScrapedJob(id, title, company, "Berlin", null, "https://example.com/" + id, description, postedAt, null);
    }

    @Test
    void storesPostingsWithCompanyAndBoardMetadata() {
        greenhouseJobs.add(scraped("1", "Engineer", "Build things", Instant.parse("2026-11-01T00:00:00Z"), "Stripe"));

        int stored = service("greenhouse:stripe").scrapeAll();

        assertThat(stored).isEqualTo(1);
        Company company = companies.findFirstByAtsTypeAndBoardToken("greenhouse", "stripe").orElseThrow();
        assertThat(company.getName()).isEqualTo("Stripe");
        Job job = jobs.findAll().get(0);
        assertThat(job.getSource()).isEqualTo("greenhouse");
        assertThat(job.getExternalId()).isEqualTo("1");
        assertThat(job.getCompanyId()).isEqualTo(company.getId());
        assertThat(job.getCreatedByProfileId()).isNull();
        assertThat(job.getPostedAt()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
        assertThat(job.getScrapedAt()).isNotNull();
        assertThat(job.getSourceUrl()).isEqualTo("https://example.com/1");
    }

    private static ScrapedJob located(String id, String title, String location, String department) {
        return new ScrapedJob(id, title, "Stripe", location, null, "https://example.com/" + id, "desc", null,
                department);
    }

    @Test
    void backfillsCategoryAndLocationsAndRerunsNeverDuplicateThem() {
        greenhouseJobs.add(located("1", "Backend Engineer", "Berlin, Germany; Remote - US", "Engineering"));
        greenhouseJobs.add(located("2", "Account Executive", "Austin, TX", "Sales"));
        JobScraperService service = service("greenhouse:stripe");

        service.scrapeAll();
        service.scrapeAll();

        Job first = jobs.findBySourceAndExternalIdIn("greenhouse", List.of("1")).get(0);
        Job second = jobs.findBySourceAndExternalIdIn("greenhouse", List.of("2")).get(0);
        assertThat(first.getCategory()).isEqualTo(CategoryClassifier.SOFTWARE);
        assertThat(second.getCategory()).isEqualTo(CategoryClassifier.SALES);
        List<JobLocation> secondRows = locations.findByJobIdIn(List.of(second.getId()));
        assertThat(secondRows).hasSize(1);
        assertThat(secondRows.get(0).getCity()).isEqualTo("Austin");
        assertThat(secondRows.get(0).getState()).isEqualTo("Texas");
        List<JobLocation> firstRows = locations.findByJobIdIn(List.of(first.getId()));
        assertThat(firstRows).extracting(JobLocation::getCountry).contains("Germany");
        assertThat(locations.count()).isEqualTo(firstRows.size() + secondRows.size());
    }

    @Test
    void aChangedLocationReplacesTheOldRows() {
        greenhouseJobs.add(located("1", "Engineer", "Berlin, Germany", null));
        JobScraperService service = service("greenhouse:stripe");
        service.scrapeAll();

        greenhouseJobs.clear();
        greenhouseJobs.add(located("1", "Engineer", "Austin, TX", null));
        service.scrapeAll();

        Job job = jobs.findBySourceAndExternalIdIn("greenhouse", List.of("1")).get(0);
        assertThat(locations.findByJobIdIn(List.of(job.getId())))
                .extracting(JobLocation::getCity).containsExactly("Austin");
    }

    @Test
    void companyNameFallsBackToTheTokenWhenTheApiGivesNone() {
        greenhouseJobs.add(scraped("1", "Engineer", "Build things", null, null));

        service("greenhouse:acme-corp").scrapeAll();

        assertThat(companies.findFirstByAtsTypeAndBoardToken("greenhouse", "acme-corp").orElseThrow().getName())
                .isEqualTo("acme-corp");
    }

    @Test
    void runningTwiceNeverDuplicatesAndAppliesUpdates() {
        greenhouseJobs.add(scraped("1", "Engineer", "Old text", null, "Stripe"));
        greenhouseJobs.add(scraped("2", "Designer", "Design", null, "Stripe"));
        JobScraperService service = service("greenhouse:stripe");
        service.scrapeAll();

        greenhouseJobs.clear();
        greenhouseJobs.add(scraped("1", "Senior Engineer", "New text", null, "Stripe"));
        greenhouseJobs.add(scraped("2", "Designer", "Design", null, "Stripe"));
        service.scrapeAll();

        assertThat(jobs.count()).isEqualTo(2);
        assertThat(companies.count()).isEqualTo(1);
        Job updated = jobs.findBySourceAndExternalIdIn("greenhouse", List.of("1")).get(0);
        assertThat(updated.getTitle()).isEqualTo("Senior Engineer");
        assertThat(updated.getDescription()).isEqualTo("New text");
    }

    @Test
    void keepsOnlyThe200NewestPostings() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 250; i++) {
            greenhouseJobs.add(scraped("id" + i, "Role " + i, "desc", base.plusSeconds(i * 60L), null));
        }

        service("greenhouse:big").scrapeAll();

        assertThat(jobs.count()).isEqualTo(200);
        assertThat(jobs.findBySourceAndExternalIdIn("greenhouse", List.of("id249", "id50"))).hasSize(2);
        assertThat(jobs.findBySourceAndExternalIdIn("greenhouse", List.of("id49"))).isEmpty();
    }

    @Test
    void blankDescriptionGetsAPlaceholderBecauseTheColumnIsRequired() {
        greenhouseJobs.add(scraped("1", "Engineer", "", null, null));

        service("greenhouse:stripe").scrapeAll();

        assertThat(jobs.findAll().get(0).getDescription()).isEqualTo(JobScraperService.NO_DESCRIPTION);
    }

    @Test
    void oneFailingBoardDoesNotStopTheOthers() {
        leverFails = true;
        greenhouseJobs.add(scraped("1", "Engineer", "Build things", null, "Stripe"));

        int stored = service("lever:palantir,greenhouse:stripe").scrapeAll();

        assertThat(stored).isEqualTo(1);
        assertThat(jobs.count()).isEqualTo(1);
        assertThat(jobs.findAll().get(0).getSource()).isEqualTo("greenhouse");
    }

    @Test
    void invalidBoardEntriesAreIgnored() {
        JobScraperService service = service("");

        assertThat(service.parseBoards(" greenhouse:stripe , bogus:x, lever, greenhouse:../etc, ,Lever:palantir "))
                .containsExactly(new JobScraperService.Board("greenhouse", "stripe"),
                        new JobScraperService.Board("lever", "palantir"));
        assertThat(service.parseBoards("")).isEmpty();
        assertThat(service.parseBoards(null)).isEmpty();
    }

    @Test
    void noBoardsConfiguredMeansNothingHappens() {
        assertThat(service("").scrapeAll()).isZero();
        assertThat(jobs.count()).isZero();
    }

    @Test
    void schedulerIsNotCreatedWhileTheScraperIsDisabled() {
        assertThat(context.getBeansOfType(JobScraperScheduler.class)).isEmpty();
    }
}
