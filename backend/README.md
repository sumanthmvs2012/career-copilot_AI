# Career Copilot backend

Spring Boot 4.1 (Java 21) modular monolith. See `../ARCHITECTURE.md` and `../docs/plan.md`.

## Run locally

1. Copy `.env.example` to `.env` and fill in `DB_PASSWORD` and `JWT_SECRET`. `.env` is gitignored.
2. Start Docker Desktop, then start the database:
   ```bash
   docker compose up -d
   ```
3. Start the API on http://localhost:8080. Flyway creates the tables on startup.
   ```bash
   mvn spring-boot:run
   ```
4. To use the real API from the frontend, set `VITE_USE_MOCK_AUTH=false` in `frontend/.env` and restart `npm run dev`.

## Commands

- `mvn test`: unit and MockMvc tests. They use in-memory H2, so no database is needed.
- `mvn package`: builds `target/backend-0.1.0.jar`.

## Layout

- `auth/`: sign up, log in, users, JWT.
- `profiles/`: the signed-in user's profile (GET and PUT `/profile`).
- `resumes/`: resume upload, read and delete (POST and GET `/resumes`, GET and DELETE `/resumes/{id}`), plus rule-based tailoring and suggestions with no AI call (POST `/resumes/{id}/tailor` 201 with `{jobId}` saves a `.txt` copy with `baseResumeId` and `jobId`, replacing an earlier copy for the same job; POST `/resumes/{id}/suggestions?jobId=` 200). Copies do not count toward the 2-resume limit, are never the base resume, and are deleted with their upload. `jobId`, `jobTitle` and `company` are set on copies and null on uploads. PDF or DOCX, 5 MB max. A profile keeps at most 2 uploaded resumes (a third upload returns 409); the newest upload is the base resume, and deleting the base promotes the newest remaining one. Files are saved under `RESUME_STORAGE_DIR` (default `./storage`) through `common/storage`.
- `matches/`: keyword matching, no AI keys. `skills.json` (classpath) is the skills dictionary (name, aliases, related); `SkillExtractor` finds skills case-insensitively on word boundaries (C++, C#, .NET and Node.js work, React Native is not also React, Java is not found in JavaScript). Resume skills are stored at upload in `resumes.skills` (older resumes are filled in on first read or match); job skills are stored in `jobs.skills` on first match. POST `/jobs/{id}/match` (200) matches the caller's base resume against a scraped job or their own pasted job and upserts the `job_matches` row; GET `/jobs/{id}/match` returns the stored result (404 if none). Errors: 404 no profile, 404 job not visible, 409 "Upload a resume first to see your match." when there is no base resume. POST `/jobs/match` also returns the match when the caller has a base resume. Result: `{jobId, resumeId, score, matchedSkills, missingSkills, nearMatchedSkills, missingKeywords, suggestedHeadline, locationFit, createdAt}`; `createdAt` is when the stored result was last computed. Resume text and skill lists are never logged.
- `applications/`: job tracking. POST `/applications` (201; body `{jobId, resumeId?, status?, coverLetter?}`, status draft or applied, cover letter up to 10000 chars), GET `/applications` (caller's own, newest first, jobs and companies loaded in batch) and PUT `/applications/{id}` (`{status?, coverLetter?}`, at least one; status draft, applied, interviewing, offer, rejected or withdrawn). The job must be a scraped job or the caller's own pasted job and the resume must be the caller's, else 404; a second application for the same job returns 409 "You already track this job." (also on a race, via the unique constraint). Moving to applied sets `appliedAt` once. `emailSent` is false and `emailSentAt` null until email tracking ships.
- `common/config`: security, CORS, password encoder.
- `common/web`: global error handler and error shape.
- `common/storage`: `StorageService` and its local-disk implementation.
- `common/ratelimit`: rate limiting for login and sign up.
- `src/main/resources/db/migration`: Flyway migrations.
- `jobs/`: pasted jobs (POST `/jobs/match`) and the job list. GET `/jobs?scope=pasted|scraped&limit=N` returns the caller's own pasted jobs and/or scraped jobs, newest first (`postedAt`, else `createdAt`). `limit` defaults to 50 and is at most 100. Optional filters (AND): `q` (title or company name contains, 1-100 chars), `source`, `remoteType`, `location` (contains, 1-100 chars), `postedWithin` (1, 7 or 30 days) and `offset` (0 or more). Filtering is built with the Criteria API in `JobSearchRepository`, with LIKE wildcards escaped. New optional filters: `category` (one of the fixed category list, case-insensitive; else 400), `country`, `state` and `city` (1-120 chars, trimmed, exact case-insensitive match; all given place params must match the same row of the job's locations). Each job in the response now has `category` (string or null) and `locations` (`[{city, state, country}]`, loaded with one query per page). Any bad value returns 400. Another user's pasted jobs are never returned.
- `GET /jobs/facets?scope=scraped|pasted&country=&state=`: dropdown options for the jobs the caller can see in that scope (default `scraped`, anything else returns 400). Returns `{categories, countries, states, cities}`, each a list of `{value, count}` with distinct-job counts, most common first and value ascending as tie-breaker. `states` are limited to `country`, `cities` to `country`/`state` and capped at 100. Another user's pasted jobs never count.
- The scraper reads the board department/team (Greenhouse `departments[].name`, Lever `categories.team` else `categories.department`), sets `jobs.category` and replaces the job's `job_locations` rows on every run in one transaction, so existing rows are backfilled on the next scrape.
- `jobs/scraper/`: the job scraper (Greenhouse and Lever public board APIs).

## Job scraper

Off by default. When enabled, a scheduled task reads each configured board once per run, stores the 200 newest postings per board as plain text, and updates existing postings by (source, external id) instead of duplicating them. One board failing does not stop the others. Logs contain only the board, counts and HTTP status. Postings that disappear from a board are not deleted.

| Variable | Default | Meaning |
|---|---|---|
| `SCRAPER_ENABLED` | `false` | Set to `true` to schedule the scraper. |
| `SCRAPER_BOARDS` | empty | Comma separated `source:token` pairs, for example `greenhouse:stripe,lever:palantir`. Sources: `greenhouse`, `lever`. Invalid entries are skipped. |
| `SCRAPER_CRON` | `0 0 * * * *` | Spring cron expression (hourly). |

Tests never call the live APIs; they use fixtures and fake sources.
