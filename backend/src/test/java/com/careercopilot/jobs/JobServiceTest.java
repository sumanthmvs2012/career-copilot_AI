package com.careercopilot.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.careercopilot.matches.MatchResponse;
import com.careercopilot.matches.MatchService;
import com.careercopilot.profiles.Profile;
import com.careercopilot.profiles.ProfileNotFoundException;
import com.careercopilot.profiles.ProfileRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    private static final JobFilter GERMANY = new JobFilter(null, null, null, null, null, 0, null, "Germany", null, null);

    private static final String DESCRIPTION = "  Build and run backend services in Java.  ";

    @Mock
    private ProfileRepository profiles;

    @Mock
    private JobRepository jobs;

    @Mock
    private CompanyRepository companies;

    @Mock
    private JobSearchRepository jobSearch;

    @Mock
    private JobLocationRepository locations;

    @Mock
    private MatchService matchService;

    @InjectMocks
    private JobService service;

    private Profile profile(long id) {
        Profile profile = new Profile(1L);
        ReflectionTestUtils.setField(profile, "id", id);
        return profile;
    }

    private Company company(long id, String name) {
        Company company = new Company(name);
        ReflectionTestUtils.setField(company, "id", id);
        return company;
    }

    private Job job(long id, String title, Long companyId, Long profileId) {
        Job job = new Job(profileId == null ? "greenhouse" : "manual", title, "description text here");
        ReflectionTestUtils.setField(job, "id", id);
        job.setCompanyId(companyId);
        job.setCreatedByProfileId(profileId);
        return job;
    }

    private void stubSave() {
        when(jobs.save(any(Job.class))).thenAnswer(inv -> {
            Job saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 9L);
            return saved;
        });
    }

    @Test
    void matchThrowsWhenTheUserHasNoProfile() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.matchPasted(1L,
                new JobMatchRequest(null, null, DESCRIPTION, null, null, null)))
                .isInstanceOf(ProfileNotFoundException.class);
        verify(jobs, never()).save(any());
    }

    @Test
    void matchReturnsTheComputedMatchWhenTheCallerHasABaseResume() {
        Profile owner = profile(5L);
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(owner));
        stubSave();
        MatchResponse computed = new MatchResponse(9L, 3L, 80, List.of("Java"), List.of(), List.of(), List.of(),
                "Engineer", null, null, null);
        when(matchService.matchIfResume(eq(owner), any(Job.class))).thenReturn(computed);

        JobMatchResponse response = service.matchPasted(1L,
                new JobMatchRequest("Engineer", null, DESCRIPTION, null, null, null));

        assertThat(response.match()).isSameAs(computed);
        assertThat(response.message()).isEqualTo("Matched against your base resume.");
        assertThat(response.job().id()).isEqualTo(9L);
    }

    @Test
    void matchSavesAManualJobWithTrimmedFieldsAndANullMatch() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile(5L)));
        stubSave();

        JobMatchResponse response = service.matchPasted(1L, new JobMatchRequest(
                "  Engineer ", "   ", DESCRIPTION, " Berlin ", "remote", " https://x.test/job "));

        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
        verify(jobs).save(captor.capture());
        Job saved = captor.getValue();
        assertThat(saved.getSource()).isEqualTo("manual");
        assertThat(saved.getCreatedByProfileId()).isEqualTo(5L);
        assertThat(saved.getTitle()).isEqualTo("Engineer");
        assertThat(saved.getDescription()).isEqualTo("Build and run backend services in Java.");
        assertThat(saved.getLocation()).isEqualTo("Berlin");
        assertThat(saved.getRemoteType()).isEqualTo("remote");
        assertThat(saved.getSourceUrl()).isEqualTo("https://x.test/job");
        assertThat(saved.getCompanyId()).isNull();
        verify(companies, never()).save(any());

        assertThat(response.match()).isNull();
        assertThat(response.message()).isEqualTo("Upload a resume to see your match.");
        assertThat(response.job().id()).isEqualTo(9L);
        assertThat(response.job().companyName()).isNull();
        assertThat(response.job().description()).isEqualTo("Build and run backend services in Java.");
        assertThat(response.job().postedAt()).isNull();
    }

    @Test
    void blankTitleAndBlankOptionalsFallBackToDefaultsAndNulls() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile(5L)));
        stubSave();

        JobMatchResponse response = service.matchPasted(1L,
                new JobMatchRequest("  ", null, DESCRIPTION, " ", " ", " "));

        assertThat(response.job().title()).isEqualTo("Pasted job");
        assertThat(response.job().location()).isNull();
        assertThat(response.job().remoteType()).isNull();
        assertThat(response.job().sourceUrl()).isNull();
    }

    @Test
    void matchReusesAnExistingCompanyIgnoringCase() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile(5L)));
        when(companies.findFirstByNameIgnoreCase("acme")).thenReturn(Optional.of(company(3L, "Acme")));
        stubSave();

        JobMatchResponse response = service.matchPasted(1L,
                new JobMatchRequest("Dev", " acme ", DESCRIPTION, null, null, null));

        verify(companies, never()).save(any());
        assertThat(response.job().companyName()).isEqualTo("Acme");
        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
        verify(jobs).save(captor.capture());
        assertThat(captor.getValue().getCompanyId()).isEqualTo(3L);
    }

    @Test
    void matchCreatesANewCompanyWithoutAnAtsType() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile(5L)));
        when(companies.findFirstByNameIgnoreCase("Newco")).thenReturn(Optional.empty());
        when(companies.save(any(Company.class))).thenAnswer(inv -> {
            Company created = inv.getArgument(0);
            ReflectionTestUtils.setField(created, "id", 4L);
            return created;
        });
        stubSave();

        JobMatchResponse response = service.matchPasted(1L,
                new JobMatchRequest("Dev", "Newco", DESCRIPTION, null, null, null));

        ArgumentCaptor<Company> captor = ArgumentCaptor.forClass(Company.class);
        verify(companies).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Newco");
        assertThat(captor.getValue().getAtsType()).isNull();
        assertThat(response.job().companyName()).isEqualTo("Newco");
    }


    @Test
    void listPassesTheCallersProfileScopeFilterAndPagingToTheSearch() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile(5L)));
        JobFilter filter = new JobFilter("dev", "lever", "remote", "berlin", null, 20);
        Job scraped = job(2L, "Scraped", 3L, null);
        when(jobSearch.search(5L, JobScope.ALL, filter, null, 20, 10)).thenReturn(List.of(scraped));
        when(companies.findAllById(Set.of(3L))).thenReturn(List.of(company(3L, "Acme")));

        List<JobResponse> result = service.list(1L, JobScope.ALL, filter, 10);

        assertThat(result).extracting(JobResponse::title).containsExactly("Scraped");
        assertThat(result).extracting(JobResponse::companyName).containsExactly("Acme");
    }

    @Test
    void listWithoutAProfilePassesANullProfileSoNoPastedJobIsVisible() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.empty());
        when(jobSearch.search(null, JobScope.ALL, JobFilter.NONE, null, 0, 50))
                .thenReturn(List.of(job(2L, "Scraped", null, null)));

        List<JobResponse> result = service.list(1L, JobScope.ALL, JobFilter.NONE, 50);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).companyName()).isNull();
        verify(companies, never()).findAllById(any());
    }

    @Test
    void scopeScrapedNeverLooksUpTheProfile() {
        when(jobSearch.search(null, JobScope.SCRAPED, JobFilter.NONE, null, 0, 50)).thenReturn(List.of());

        assertThat(service.list(1L, JobScope.SCRAPED, JobFilter.NONE, 50)).isEmpty();
        verify(profiles, never()).findByUserId(any());
    }

    @Test
    void postedWithinBecomesACutoffThatMatchesTheNumberOfDays() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile(5L)));
        ArgumentCaptor<java.time.Instant> cutoff = ArgumentCaptor.forClass(java.time.Instant.class);
        when(jobSearch.search(eq(5L), eq(JobScope.ALL), any(), cutoff.capture(), eq(0), eq(50)))
                .thenReturn(List.of());
        java.time.Instant before = java.time.Instant.now();

        service.list(1L, JobScope.ALL, new JobFilter(null, null, null, null, 7, 0), 50);

        java.time.Instant after = java.time.Instant.now();
        assertThat(cutoff.getValue()).isBetween(before.minus(7, java.time.temporal.ChronoUnit.DAYS),
                after.minus(7, java.time.temporal.ChronoUnit.DAYS));
    }

    @Test
    void listLoadsTheLocationsOfThePageWithOneQuery() {
        Job first = job(2L, "First", null, null);
        Job second = job(3L, "Second", null, null);
        first.setCategory("Sales");
        when(jobSearch.search(null, JobScope.SCRAPED, JobFilter.NONE, null, 0, 50))
                .thenReturn(List.of(first, second));
        JobLocation austin = new JobLocation(2L, "Austin", "Texas", "United States");
        JobLocation berlin = new JobLocation(2L, "Berlin", null, "Germany");
        ReflectionTestUtils.setField(austin, "id", 1L);
        ReflectionTestUtils.setField(berlin, "id", 2L);
        when(locations.findByJobIdIn(List.of(2L, 3L))).thenReturn(List.of(berlin, austin));

        List<JobResponse> result = service.list(1L, JobScope.SCRAPED, JobFilter.NONE, 50);

        assertThat(result.get(0).category()).isEqualTo("Sales");
        assertThat(result.get(0).locations()).containsExactly(
                new LocationDto("Austin", "Texas", "United States"), new LocationDto("Berlin", null, "Germany"));
        assertThat(result.get(1).locations()).isEmpty();
        verify(locations).findByJobIdIn(any());
    }

    @Test
    void emptyPageDoesNotQueryLocations() {
        when(jobSearch.search(null, JobScope.SCRAPED, JobFilter.NONE, null, 0, 50)).thenReturn(List.of());

        service.list(1L, JobScope.SCRAPED, JobFilter.NONE, 50);

        verify(locations, never()).findByJobIdIn(any());
    }

    @Test
    void matchSetsTheCategoryFromTheTitleAndStoresParsedLocations() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile(5L)));
        stubSave();
        when(locations.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        JobMatchResponse response = service.matchPasted(1L, new JobMatchRequest(
                "Senior Backend Engineer", null, DESCRIPTION, "Austin, TX", null, null));

        ArgumentCaptor<Job> job = ArgumentCaptor.forClass(Job.class);
        verify(jobs).save(job.capture());
        assertThat(job.getValue().getCategory()).isEqualTo("Software Engineering");
        assertThat(response.job().category()).isEqualTo("Software Engineering");
        assertThat(response.job().locations()).containsExactly(new LocationDto("Austin", "Texas", "United States"));
    }

    @Test
    void matchWithoutALocationStoresNoLocationRows() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile(5L)));
        stubSave();

        JobMatchResponse response = service.matchPasted(1L,
                new JobMatchRequest("Dev", null, DESCRIPTION, null, null, null));

        assertThat(response.job().locations()).isEmpty();
        verify(locations, never()).saveAll(any());
    }

    @Test
    void facetsForScrapedScopeNeverLookUpTheProfile() {
        JobFacets expected = new JobFacets(List.of(new FacetValue("Sales", 2)), List.of(), List.of(), List.of());
        when(jobSearch.facets(null, JobScope.SCRAPED, GERMANY, null)).thenReturn(expected);

        assertThat(service.facets(1L, JobScope.SCRAPED, GERMANY)).isSameAs(expected);
        verify(profiles, never()).findByUserId(any());
    }

    @Test
    void facetsForPastedScopeUseTheCallersProfileOnly() {
        JobFacets expected = new JobFacets(List.of(), List.of(), List.of(), List.of());
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile(5L)));
        when(jobSearch.facets(5L, JobScope.PASTED, JobFilter.NONE, null)).thenReturn(expected);

        assertThat(service.facets(1L, JobScope.PASTED, JobFilter.NONE)).isSameAs(expected);
    }

    @Test
    void facetsForPastedScopeWithoutAProfilePassANullProfile() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.empty());
        when(jobSearch.facets(null, JobScope.PASTED, JobFilter.NONE, null))
                .thenReturn(new JobFacets(List.of(), List.of(), List.of(), List.of()));

        assertThat(service.facets(1L, JobScope.PASTED, JobFilter.NONE).countries()).isEmpty();
    }
}
