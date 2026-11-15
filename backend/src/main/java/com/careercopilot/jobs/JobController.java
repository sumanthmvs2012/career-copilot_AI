package com.careercopilot.jobs;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The user id always comes from the token, so pasted jobs are only reachable by their owner. */
@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping("/match")
    @ResponseStatus(HttpStatus.CREATED)
    public JobMatchResponse match(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody JobMatchRequest request) {
        return jobService.matchPasted(userId(jwt), request);
    }

    @GetMapping
    public List<JobResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String limit,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String remoteType,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String postedWithin,
            @RequestParam(required = false) String offset,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String city) {
        JobScope parsedScope = JobScope.parse(scope);
        int parsedLimit = parseLimit(limit);
        JobFilter filter = JobFilter.parse(q, source, remoteType, location, postedWithin, offset, category, country,
                state, city);
        return jobService.list(userId(jwt), parsedScope, filter, parsedLimit);
    }

    /** Scope defaults to scraped here (unlike the list) and only scraped or pasted are accepted. */
    @GetMapping("/facets")
    public JobFacets facets(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String state) {
        JobScope parsedScope = scope == null || scope.isBlank() ? JobScope.SCRAPED : JobScope.parse(scope);
        return jobService.facets(userId(jwt), parsedScope, JobFilter.place(country, "country"),
                JobFilter.place(state, "state"));
    }

    private static int parseLimit(String value) {
        if (value == null || value.isBlank()) {
            return JobService.DEFAULT_LIMIT;
        }
        try {
            int limit = Integer.parseInt(value.trim());
            if (limit >= 1 && limit <= JobService.MAX_LIMIT) {
                return limit;
            }
        } catch (NumberFormatException ex) {
            // Falls through to the same 400 as an out-of-range number.
        }
        throw new InvalidJobQueryException("Limit must be a number from 1 to " + JobService.MAX_LIMIT + ".");
    }

    private static Long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
