package com.careercopilot.jobs.scraper;

import com.careercopilot.jobs.Company;
import com.careercopilot.jobs.CompanyRepository;
import com.careercopilot.jobs.Job;
import com.careercopilot.jobs.JobLocation;
import com.careercopilot.jobs.JobLocationRepository;
import com.careercopilot.jobs.JobRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientResponseException;

/**
 * Reads every configured board and stores its postings. Logs contain only the board, counts and the HTTP status,
 * never posting text.
 */
@Service
public class JobScraperService {

    static final int MAX_JOBS_PER_BOARD = 200;
    static final String NO_DESCRIPTION = "No description was provided for this posting.";

    // Tokens go into a URL path, so anything but plain slug characters is refused.
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    private static final Logger log = LoggerFactory.getLogger(JobScraperService.class);

    private final ScraperProperties properties;
    private final Map<String, JobSource> sources = new HashMap<>();
    private final CompanyRepository companies;
    private final JobRepository jobs;
    private final JobLocationRepository locations;
    private final TransactionTemplate transactions;

    public JobScraperService(ScraperProperties properties, List<JobSource> sources, CompanyRepository companies,
            JobRepository jobs, JobLocationRepository locations, TransactionTemplate transactions) {
        this.properties = properties;
        sources.forEach(source -> this.sources.put(source.source(), source));
        this.companies = companies;
        this.jobs = jobs;
        this.locations = locations;
        this.transactions = transactions;
    }

    /** One board failing never stops the others. Returns how many jobs were created or updated. */
    public int scrapeAll() {
        int total = 0;
        for (Board board : parseBoards(properties.boards())) {
            try {
                total += scrapeBoard(board);
            } catch (RestClientResponseException ex) {
                log.warn("Scrape failed for {}:{} with HTTP status {}", board.source(), board.token(),
                        ex.getStatusCode().value());
            } catch (Exception ex) {
                // Only the exception type is logged; messages can echo response content.
                log.warn("Scrape failed for {}:{} ({})", board.source(), board.token(), ex.getClass().getSimpleName());
            }
        }
        return total;
    }

    private int scrapeBoard(Board board) {
        JobSource source = sources.get(board.source());
        // The network call stays outside the transaction; only the writes below run inside one.
        List<ScrapedJob> fetched = source.fetch(board.token());
        Integer stored = transactions.execute(status -> store(board, fetched));
        return stored == null ? 0 : stored;
    }

    private int store(Board board, List<ScrapedJob> fetched) {
        // Keep the newest postings; unknown dates sort last.
        Map<String, ScrapedJob> newest = new LinkedHashMap<>();
        Comparator<Instant> newestFirst = Comparator.nullsLast(Comparator.<Instant>reverseOrder());
        fetched.stream()
                .sorted(Comparator.comparing(ScrapedJob::postedAt, newestFirst))
                .forEach(job -> newest.putIfAbsent(job.externalId(), job));
        List<ScrapedJob> kept = newest.values().stream().limit(MAX_JOBS_PER_BOARD).toList();

        Company company = findOrCreateCompany(board, kept);
        Map<String, Job> existing = new HashMap<>();
        if (!kept.isEmpty()) {
            jobs.findBySourceAndExternalIdIn(board.source(), kept.stream().map(ScrapedJob::externalId).toList())
                    .forEach(job -> existing.put(job.getExternalId(), job));
        }

        Instant now = Instant.now();
        List<Job> toSave = new ArrayList<>();
        int created = 0;
        for (ScrapedJob scraped : kept) {
            String description = scraped.description() == null || scraped.description().isBlank()
                    ? NO_DESCRIPTION : scraped.description();
            Job job = existing.get(scraped.externalId());
            if (job == null) {
                job = new Job(board.source(), scraped.title(), description);
                job.setExternalId(scraped.externalId());
                job.setCompanyId(company.getId());
                created++;
            } else {
                job.setTitle(scraped.title());
                job.setDescription(description);
            }
            job.setLocation(scraped.location());
            job.setRemoteType(scraped.remoteType());
            job.setSourceUrl(scraped.sourceUrl());
            job.setPostedAt(scraped.postedAt());
            job.setScrapedAt(now);
            // Re-derived on every run so rows scraped before categories existed are backfilled.
            job.setCategory(CategoryClassifier.classify(scraped.department(), scraped.title()));
            toSave.add(job);
        }
        List<Job> saved = jobs.saveAll(toSave);
        replaceLocations(saved, kept);
        log.info("Scraped {}:{} fetched={} kept={} created={} updated={}", board.source(), board.token(),
                fetched.size(), kept.size(), created, kept.size() - created);
        return toSave.size();
    }

    /** Deleting then inserting keeps reruns idempotent; saveAll returns the jobs in the same order as kept. */
    private void replaceLocations(List<Job> saved, List<ScrapedJob> kept) {
        if (saved.isEmpty()) {
            return;
        }
        locations.deleteByJobIds(saved.stream().map(Job::getId).toList());
        List<JobLocation> rows = new ArrayList<>();
        for (int i = 0; i < saved.size(); i++) {
            Long jobId = saved.get(i).getId();
            for (ParsedLocation parsed : LocationParser.parse(kept.get(i).location())) {
                rows.add(new JobLocation(jobId, parsed.city(), parsed.state(), parsed.country()));
            }
        }
        locations.saveAll(rows);
    }

    private Company findOrCreateCompany(Board board, List<ScrapedJob> scraped) {
        return companies.findFirstByAtsTypeAndBoardToken(board.source(), board.token()).orElseGet(() -> {
            String name = scraped.stream()
                    .map(ScrapedJob::companyName)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse(board.token());
            Company company = new Company(name);
            company.setAtsType(board.source());
            company.setBoardToken(board.token());
            return companies.save(company);
        });
    }

    List<Board> parseBoards(String config) {
        List<Board> boards = new ArrayList<>();
        if (config == null || config.isBlank()) {
            return boards;
        }
        for (String entry : config.split(",")) {
            String[] parts = entry.trim().split(":", 2);
            String source = parts[0].trim().toLowerCase(Locale.ROOT);
            String token = parts.length == 2 ? parts[1].trim() : "";
            if (!sources.containsKey(source) || !TOKEN.matcher(token).matches()) {
                log.warn("Ignoring invalid scraper board entry (expected source:token for a known source)");
                continue;
            }
            boards.add(new Board(source, token));
        }
        return boards;
    }

    record Board(String source, String token) {
    }
}
