package com.careercopilot.jobs;

import com.careercopilot.jobs.scraper.CategoryClassifier;
import com.careercopilot.jobs.scraper.LocationParser;
import com.careercopilot.jobs.scraper.ParsedLocation;
import com.careercopilot.profiles.Profile;
import com.careercopilot.profiles.ProfileNotFoundException;
import com.careercopilot.profiles.ProfileRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobService {

    static final String NOT_AVAILABLE_MESSAGE = "Matching is not available yet; your job is saved.";
    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 100;
    static final String DEFAULT_TITLE = "Pasted job";

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final ProfileRepository profiles;
    private final JobRepository jobs;
    private final CompanyRepository companies;
    private final JobSearchRepository jobSearch;
    private final JobLocationRepository locations;

    public JobService(ProfileRepository profiles, JobRepository jobs, CompanyRepository companies,
            JobSearchRepository jobSearch, JobLocationRepository locations) {
        this.profiles = profiles;
        this.jobs = jobs;
        this.companies = companies;
        this.jobSearch = jobSearch;
        this.locations = locations;
    }

    @Transactional
    public JobMatchResponse matchPasted(Long userId, JobMatchRequest request) {
        Profile profile = profiles.findByUserId(userId).orElseThrow(ProfileNotFoundException::new);

        String title = blankToNull(request.title());
        String companyName = blankToNull(request.companyName());
        Company company = companyName == null ? null : findOrCreateCompany(companyName);

        String storedTitle = title == null ? DEFAULT_TITLE : title;
        Job job = new Job("manual", storedTitle, request.description().trim());
        job.setCreatedByProfileId(profile.getId());
        job.setCompanyId(company == null ? null : company.getId());
        job.setLocation(blankToNull(request.location()));
        job.setRemoteType(blankToNull(request.remoteType()));
        job.setSourceUrl(blankToNull(request.sourceUrl()));
        job.setCategory(CategoryClassifier.classify(null, storedTitle));
        Job saved = jobs.save(job);
        List<LocationDto> savedLocations = saveLocations(saved.getId(), job.getLocation());

        log.info("Pasted job saved for profile {}", profile.getId());
        return new JobMatchResponse(
                JobResponse.from(saved, company == null ? null : company.getName(), savedLocations), null,
                NOT_AVAILABLE_MESSAGE);
    }

    @Transactional(readOnly = true)
    public List<JobResponse> list(Long userId, JobScope scope, JobFilter filter, int limit) {
        // Pasted jobs are matched to the caller's own profile only, so another user's jobs can never appear.
        // Scraped jobs need no profile, so the profile is only looked up when pasted jobs are in scope.
        Long profileId = scope == JobScope.SCRAPED
                ? null
                : profiles.findByUserId(userId).map(Profile::getId).orElse(null);
        Instant postedAfter = filter.postedWithin() == null
                ? null
                : Instant.now().minus(filter.postedWithin(), ChronoUnit.DAYS);
        List<Job> visible = jobSearch.search(profileId, scope, filter, postedAfter, filter.offset(), limit);

        Set<Long> companyIds = visible.stream()
                .map(Job::getCompanyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> names = new HashMap<>();
        if (!companyIds.isEmpty()) {
            for (Company company : companies.findAllById(companyIds)) {
                names.put(company.getId(), company.getName());
            }
        }
        Map<Long, List<LocationDto>> placesByJob = loadLocations(visible);
        return visible.stream()
                .map(job -> JobResponse.from(job, job.getCompanyId() == null ? null : names.get(job.getCompanyId()),
                        placesByJob.getOrDefault(job.getId(), List.of())))
                .toList();
    }

    /** Facet counts follow the same visibility rule as the list, so another user's pasted jobs never appear. */
    @Transactional(readOnly = true)
    public JobFacets facets(Long userId, JobScope scope, String country, String state) {
        Long profileId = scope == JobScope.SCRAPED
                ? null
                : profiles.findByUserId(userId).map(Profile::getId).orElse(null);
        return jobSearch.facets(profileId, scope, country, state);
    }

    /** One query for the whole page, so listing never loads locations job by job. */
    private Map<Long, List<LocationDto>> loadLocations(List<Job> page) {
        Map<Long, List<LocationDto>> byJob = new HashMap<>();
        if (page.isEmpty()) {
            return byJob;
        }
        List<JobLocation> rows = new ArrayList<>(locations.findByJobIdIn(page.stream().map(Job::getId).toList()));
        rows.sort(Comparator.comparing(JobLocation::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        for (JobLocation row : rows) {
            byJob.computeIfAbsent(row.getJobId(), id -> new ArrayList<>()).add(LocationDto.from(row));
        }
        return byJob;
    }

    private List<LocationDto> saveLocations(Long jobId, String location) {
        List<JobLocation> rows = new ArrayList<>();
        if (location != null) {
            for (ParsedLocation parsed : LocationParser.parse(location)) {
                rows.add(new JobLocation(jobId, parsed.city(), parsed.state(), parsed.country()));
            }
        }
        if (rows.isEmpty()) {
            return List.of();
        }
        return locations.saveAll(rows).stream().map(LocationDto::from).toList();
    }

    private Company findOrCreateCompany(String name) {
        return companies.findFirstByNameIgnoreCase(name).orElseGet(() -> companies.save(new Company(name)));
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
