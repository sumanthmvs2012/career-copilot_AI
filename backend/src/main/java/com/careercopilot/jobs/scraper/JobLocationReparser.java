package com.careercopilot.jobs.scraper;

import com.careercopilot.jobs.Job;
import com.careercopilot.jobs.JobLocation;
import com.careercopilot.jobs.JobLocationRepository;
import com.careercopilot.jobs.JobRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Re-derives job_locations from each job's stored free-text location with the current LocationParser, so rows
 * written by an older parser get fixed. Idempotent: only jobs whose parsed rows differ from the stored ones are
 * touched, and no job is ever deleted or otherwise changed. Logs contain counts only.
 */
@Service
public class JobLocationReparser {

    static final int BATCH_SIZE = 500;
    private static final Logger log = LoggerFactory.getLogger(JobLocationReparser.class);

    private final JobRepository jobs;
    private final JobLocationRepository locations;
    private final TransactionTemplate transactions;

    public JobLocationReparser(JobRepository jobs, JobLocationRepository locations, TransactionTemplate transactions) {
        this.jobs = jobs;
        this.locations = locations;
        this.transactions = transactions;
    }

    /** Returns how many jobs had their locations rewritten. */
    public int reparseAll() {
        int changed = 0;
        int seen = 0;
        long afterId = 0;
        while (true) {
            long cursor = afterId;
            Batch batch = transactions.execute(status -> reparseBatch(cursor));
            if (batch == null || batch.size() == 0) {
                break;
            }
            seen += batch.size();
            changed += batch.changed();
            afterId = batch.lastId();
        }
        log.info("Location reparse finished, jobs checked={} rewritten={}", seen, changed);
        return changed;
    }

    private Batch reparseBatch(long afterId) {
        List<Job> page = jobs.findByLocationIsNotNullAndIdGreaterThanOrderByIdAsc(afterId, PageRequest.of(0, BATCH_SIZE));
        if (page.isEmpty()) {
            return new Batch(0, 0, afterId);
        }
        List<Long> ids = page.stream().map(Job::getId).toList();
        Map<Long, List<JobLocation>> stored = new HashMap<>();
        for (JobLocation row : locations.findByJobIdIn(ids)) {
            stored.computeIfAbsent(row.getJobId(), id -> new ArrayList<>()).add(row);
        }
        List<Long> changedIds = new ArrayList<>();
        List<JobLocation> newRows = new ArrayList<>();
        for (Job job : page) {
            List<ParsedLocation> parsed = LocationParser.parse(job.getLocation());
            if (!sameAs(stored.getOrDefault(job.getId(), List.of()), parsed)) {
                changedIds.add(job.getId());
                for (ParsedLocation p : parsed) {
                    newRows.add(new JobLocation(job.getId(), p.city(), p.state(), p.country()));
                }
            }
        }
        if (!changedIds.isEmpty()) {
            locations.deleteByJobIds(changedIds);
            locations.saveAll(newRows);
        }
        return new Batch(page.size(), changedIds.size(), page.get(page.size() - 1).getId());
    }

    private static boolean sameAs(List<JobLocation> stored, List<ParsedLocation> parsed) {
        if (stored.size() != parsed.size()) {
            return false;
        }
        List<JobLocation> sorted = new ArrayList<>(stored);
        sorted.sort((a, b) -> Long.compare(a.getId(), b.getId()));
        for (int i = 0; i < parsed.size(); i++) {
            JobLocation s = sorted.get(i);
            ParsedLocation p = parsed.get(i);
            if (!Objects.equals(s.getCity(), p.city()) || !Objects.equals(s.getState(), p.state())
                    || !Objects.equals(s.getCountry(), p.country())) {
                return false;
            }
        }
        return true;
    }

    private record Batch(int size, int changed, long lastId) {
    }
}
