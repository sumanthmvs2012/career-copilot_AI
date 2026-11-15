package com.careercopilot.jobs;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Repository;

/**
 * Filtered job listing built with the Criteria API (no SQL strings), so it runs on H2 and PostgreSQL.
 * User text only ever reaches the database as a bound parameter, with LIKE wildcards escaped.
 */
@Repository
public class JobSearchRepository {

    static final char ESCAPE = '\\';
    static final int MAX_CITIES = 100;

    @PersistenceContext
    private EntityManager em;

    /**
     * @param profileId the caller's profile id, or null when the caller has no profile (then no pasted job is visible)
     * @param postedAfter only jobs whose coalesce(postedAt, createdAt) is at or after this time; null for no limit
     */
    public List<Job> search(Long profileId, JobScope scope, JobFilter filter, Instant postedAfter, int offset,
            int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Job> query = cb.createQuery(Job.class);
        Root<Job> job = query.from(Job.class);
        Expression<Instant> sortTime = cb.coalesce(job.<Instant>get("postedAt"), job.<Instant>get("createdAt"));

        List<Predicate> where = new ArrayList<>();
        where.add(visible(cb, job, profileId, scope));
        if (filter.source() != null) {
            where.add(cb.equal(job.get("source"), filter.source()));
        }
        if (filter.remoteType() != null) {
            where.add(cb.equal(job.get("remoteType"), filter.remoteType()));
        }
        if (filter.location() != null) {
            where.add(contains(cb, job.<String>get("location"), filter.location()));
        }
        if (filter.q() != null) {
            Subquery<Long> company = query.subquery(Long.class);
            Root<Company> c = company.from(Company.class);
            company.select(c.<Long>get("id")).where(
                    cb.equal(c.get("id"), job.get("companyId")),
                    contains(cb, c.<String>get("name"), filter.q()));
            // A job without a company still matches on its title.
            where.add(cb.or(contains(cb, job.<String>get("title"), filter.q()), cb.exists(company)));
        }
        if (postedAfter != null) {
            where.add(cb.greaterThanOrEqualTo(sortTime, postedAfter));
        }
        if (filter.category() != null) {
            // Stored categories come from the fixed list, and the filter holds the canonical spelling.
            where.add(cb.equal(cb.lower(job.<String>get("category")), filter.category().toLowerCase(Locale.ROOT)));
        }
        if (filter.country() != null || filter.state() != null || filter.city() != null) {
            // One EXISTS keeps every given place part on the same location row of the job.
            Subquery<Long> place = query.subquery(Long.class);
            Root<JobLocation> l = place.from(JobLocation.class);
            List<Predicate> onRow = new ArrayList<>();
            onRow.add(cb.equal(l.get("jobId"), job.get("id")));
            addPlace(cb, onRow, l, "country", filter.country());
            addPlace(cb, onRow, l, "state", filter.state());
            addPlace(cb, onRow, l, "city", filter.city());
            place.select(l.<Long>get("id")).where(onRow.toArray(new Predicate[0]));
            where.add(cb.exists(place));
        }

        query.select(job).where(where.toArray(new Predicate[0]))
                .orderBy(cb.desc(sortTime), cb.desc(job.get("id")));
        return em.createQuery(query).setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    /**
     * Dropdown options for the jobs the caller can see in the scope, each with its distinct job count, most common
     * first. States are limited to the given country and cities to the given country and state.
     */
    public JobFacets facets(Long profileId, JobScope scope, String country, String state) {
        List<FacetValue> categories = categoryFacets(profileId, scope);
        List<FacetValue> countries = placeFacets(profileId, scope, "country", null, null, 0);
        List<FacetValue> states = placeFacets(profileId, scope, "state", country, null, 0);
        List<FacetValue> cities = placeFacets(profileId, scope, "city", country, state, MAX_CITIES);
        return new JobFacets(categories, countries, states, cities);
    }

    private List<FacetValue> categoryFacets(Long profileId, JobScope scope) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Job> job = query.from(Job.class);
        Expression<Long> count = cb.countDistinct(job.<Long>get("id"));
        query.multiselect(job.<String>get("category"), count)
                .where(visible(cb, job, profileId, scope), cb.isNotNull(job.get("category")))
                .groupBy(job.get("category"))
                .orderBy(cb.desc(count), cb.asc(job.get("category")));
        return toFacets(em.createQuery(query).getResultList());
    }

    private List<FacetValue> placeFacets(Long profileId, JobScope scope, String field, String country, String state,
            int max) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Job> job = query.from(Job.class);
        Root<JobLocation> l = query.from(JobLocation.class);
        Expression<Long> count = cb.countDistinct(job.<Long>get("id"));
        List<Predicate> where = new ArrayList<>();
        where.add(cb.equal(l.get("jobId"), job.get("id")));
        where.add(visible(cb, job, profileId, scope));
        where.add(cb.isNotNull(l.get(field)));
        addPlace(cb, where, l, "country", country);
        addPlace(cb, where, l, "state", state);
        query.multiselect(l.<String>get(field), count)
                .where(where.toArray(new Predicate[0]))
                .groupBy(l.get(field))
                .orderBy(cb.desc(count), cb.asc(l.get(field)));
        TypedQuery<Tuple> typed = em.createQuery(query);
        if (max > 0) {
            typed.setMaxResults(max);
        }
        return toFacets(typed.getResultList());
    }

    private static List<FacetValue> toFacets(List<Tuple> rows) {
        List<FacetValue> values = new ArrayList<>();
        for (Tuple row : rows) {
            values.add(new FacetValue(row.get(0, String.class), row.get(1, Long.class)));
        }
        return values;
    }

    /** Case-insensitive exact match of one place column; the value is bound, never concatenated. */
    private static void addPlace(CriteriaBuilder cb, List<Predicate> where, Root<JobLocation> location, String field,
            String value) {
        if (value != null) {
            where.add(cb.equal(cb.lower(location.<String>get(field)), value.toLowerCase(Locale.ROOT)));
        }
    }

    /** Scraped jobs have no creating profile; pasted jobs are matched to the caller's profile only. */
    private static Predicate visible(CriteriaBuilder cb, Root<Job> job, Long profileId, JobScope scope) {
        Predicate scraped = cb.isNull(job.get("createdByProfileId"));
        Predicate own = profileId == null ? cb.disjunction() : cb.equal(job.get("createdByProfileId"), profileId);
        return switch (scope) {
            case SCRAPED -> scraped;
            case PASTED -> own;
            case ALL -> cb.or(scraped, own);
        };
    }

    private static Predicate contains(CriteriaBuilder cb, Expression<String> column, String text) {
        String pattern = "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
        return cb.like(cb.lower(column), pattern, ESCAPE);
    }

    /** Makes %, _ and the escape character match themselves. */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
