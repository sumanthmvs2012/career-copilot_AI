# Project Plan: AI Job Application and Career Copilot

## Summary

A web app for job seekers that:

1. Takes a resume upload and pulls out the skills.
2. Matches a resume against job descriptions (a score plus the missing skills).
3. Suggests resume improvements for a specific job.
4. Tracks applications on a dashboard.
5. Writes interview questions for a job.
6. Runs an agent that collects new postings every hour and writes an application draft for each one.

**Stack:** React + Vite (frontend), Spring Boot modular monolith (backend), PostgreSQL with pgvector on Neon, and Claude through the Anthropic API. The AI layer uses RAG, LLM calls and an agent. Hosting is Vercel, Render and Neon. Errors go to Sentry.

Source of truth: `PRD.md`, `ARCHITECTURE.md`, `DATABASE.md`, `DESIGN_SYSTEM.md`, `SECURITY.md`, `CODE_STYLE.md`, `TESTING.md`.

## Phases

| # | Phase | Goal | Key deliverables | Exit criteria |
|---|-------|------|------------------|---------------|
| 1 | Planning | Lock the scope and settle the gaps before writing code | This plan, a requirements map, a gap list, answered open questions, an updated API and schema | Every open question answered or deferred, and the docs updated to match |
| 2 | UI | Screens and components that follow the design system | Vite app with pages for auth, profile, resume upload, job match, dashboard, interview questions and drafts. Each screen has loading, empty, error and success states | Screens work on phone and desktop. Vitest + RTL tests pass. `npm run lint` passes |
| 3 | Backend | Spring Boot feature modules and REST API | Modules for auth, profiles, resumes, jobs, applications, ai and agent. Global error handler, JWT, ownership checks, rate limiting | MockMvc tests pass for the critical flows. `mvn test` passes |
| 4 | Database | Schema, migrations and vector storage | Flyway migrations for every table in DATABASE.md, the pgvector extension and embedding columns, seed data | Migrations run cleanly on a fresh database and have been tested |
| 5 | AI | Skill extraction, matching, suggestions, interview questions and the agent | PDF/DOCX text extraction, embeddings, RAG retrieval, Claude prompts, the hourly scraper and the draft agent | Match returns a score and missing skills. At least 20 questions per job. Drafts are created for new postings |
| 6 | Testing | Meet the TESTING.md targets | Unit, integration and frontend tests, and tests for the critical flows | About 80% coverage on backend services. All critical flows pass |
| 7 | DevOps | CI and repeatable builds | CI pipeline (test, lint, build), Dockerfile for the backend, environment variable setup | CI is green on the main branch. No secrets in the repo |
| 8 | Deployment | Ship to production | Frontend on Vercel, backend on Render, database on Neon, migrations run on deploy | The app is reachable and a smoke test of the critical flows passes |
| 9 | Monitoring | See what happens in production | Sentry for frontend and backend, structured SLF4J/Logback logs, health checks, alerts for scraper and agent runs | Errors reach Sentry. Logs contain no secrets and no resume text |

---

## Phase 1: Planning (in progress)

**Progress:** the Phase 2 login page has started early. `frontend/` uses React + Vite. The token is kept in memory and copied to sessionStorage. The page is tested against a mocked `/auth/login`. The sign-up page is also done (email, password of at least 8 characters, confirm password) and uses a mocked `/auth/signup`. The 8-character minimum is an assumption until SECURITY.md sets a password policy.

**Backend (Phase 3, started):** `backend/` uses Spring Boot 4.1 on Java 21. It has the auth module (`POST /auth/signup`, `POST /auth/login`), the profiles module (`GET/PUT /profile`, owner-only via the JWT subject) and the resumes module (`POST /resumes` multipart upload with PDF/DOCX checks, 5 MB limit, local-disk `StorageService` and PDFBox/POI text extraction; `GET /resumes`, `GET /resumes/{id}`), the Flyway migrations V1 users, V2 profiles, V3 resumes, JWT, rate limiting, a global error handler and CORS. 62 tests pass (unit + MockMvc on H2). On 2026-11-15 it was run against a real Postgres (pgvector/pg17 in Docker): all migrations applied and every endpoint was exercised over HTTP and in the browser.

**Frontend (Phase 2, continuing):** the dashboard shows a profile card, a resume card and the applications table (PRD columns, empty state for now, behind a stub service) with loading, empty, error and success states. A profile page creates and edits the profile; a resume page uploads or replaces the base resume. `/dashboard`, `/profile` and `/resume` are protected routes. 116 tests pass; lint and build pass.

**Schema decision (2026-11-15):** `resumes.skills` (jsonb) and the `embedding vector(1024)` columns are added by the Phase 5 AI migration rather than now, because the tests run on H2, which cannot run those types; that migration also moves the backend tests to Testcontainers with pgvector (TESTING.md). `resumes.job_id` arrives with the jobs migration so it can carry its foreign key.

### 1.1 Requirements map

| Feature | PRD | API (ARCHITECTURE.md) | Tables (DATABASE.md) | Status |
|---------|-----|-----------------------|----------------------|--------|
| Sign up / log in | implied | `POST /auth/signup`, `POST /auth/login` | users | Covered |
| Profile create/edit | yes | `GET/PUT /profile` | profiles | Covered |
| Upload resume + extract skills | yes | `POST /resumes`, `GET /resumes/{id}` | resumes (parsed_text) | **Gap:** no place to store extracted skills or embeddings |
| Tailored resume copy per job | yes | none | resumes (is_base) | **Gap:** no endpoint, and no link from a tailored copy to its job or base resume |
| Match a pasted job description | yes ("paste a job description") | `POST /jobs/{id}/match` (scraped job only) | job_matches | **Conflict:** the PRD says the user pastes a description, but the API only matches scraped jobs by id |
| Resume improvement suggestions | yes | none | none | **Gap:** no endpoint and no table |
| Application dashboard (status, date, email sent and when) | yes | `POST/GET /applications` | applications, email_logs | **Gap:** no endpoint to update status. Nothing says what sends the email or to whom |
| Interview questions (20 or more, copy in one click, based on company **and resume**) | yes | `GET /jobs/{id}/interview-questions` | interview_questions (job_id only) | **Gap:** questions are tied to the job only, so they can't reflect the user's resume |
| Hourly scraper (e.g. Greenhouse) | yes | none (scheduled task) | companies, jobs | Covered. How each source is fetched is still open |
| Agent writes an application draft per new posting | yes | none | none | **Gap:** no drafts table and no endpoint |
| RAG / pgvector | stack | none | none | **Gap:** no vector columns or chunk table in DATABASE.md |

### 1.2 Gaps and fixes (approved 2026-11-15, applied to DATABASE.md, ARCHITECTURE.md and AGENTS.md)

1. **Skills and embeddings:** add `skills` (text[] or jsonb) and `embedding vector(n)` to `resumes`, and `embedding` to `jobs`. A separate `resume_chunks` table may be needed for RAG.
2. **Tailored resumes:** add `base_resume_id` and `job_id` (both nullable) to `resumes`. Add endpoints `POST /resumes/{id}/tailor` and `GET /resumes`.
3. **Pasted job descriptions:** add `POST /jobs/match` that takes raw text, or save the pasted text as a `jobs` row whose source is "manual".
4. **Suggestions:** add `POST /resumes/{id}/suggestions?jobId=`. Decide whether suggestions are saved or generated each time.
5. **Application status:** add `PUT /applications/{id}` and define the allowed status values (e.g. draft, applied, interviewing, offer, rejected).
6. **Interview questions:** add `profile_id` or `application_id` to `interview_questions`.
7. **Application drafts:** add an `application_drafts` table (id, profile_id, job_id, resume_id, cover_letter, status, created_at) and `GET /drafts`. Alternatively, reuse `applications` with status `draft`.
8. **Docs location:** AGENTS.md says the docs live in a `docs/` folder, but they are at the repo root. Either move them into `docs/` or update AGENTS.md.

### 1.3 Decisions (answered 2026-11-15)

1. **Submitting applications:** the agent writes a draft and asks before submitting. Users can turn on auto-submit in their settings, and then the agent submits without asking. Auto-submit only works where a job board allows applications through an API. Any site with a CAPTCHA or bot check always falls back to asking you to submit it yourself.
2. **Email tracking:** `email_logs` tracks replies from employers, tagged as rejection, interview/acceptance, or other, linked to the application. It also tracks follow-up reminders after you apply. Tracking replies means reading your mailbox with your OAuth consent (see 1.4).
3. **Which postings get drafts:** only postings with a match score of 70 or more.
4. **Job sources:** every public job board API and company careers board. First release: Greenhouse, Lever and Ashby, then Workable and SmartRecruiters. Sources must allow automated access, so LinkedIn and Indeed (whose terms forbid scraping) are left out unless an official API is used. Sources are behind one `JobSource` interface, so adding a board means adding one class.
5. **Resume storage:** for now, files are saved to local disk behind a `StorageService` interface. Before launch this switches to Cloudflare R2, which is S3-compatible, has a 10 GB free tier and charges no download fees. Render's disk is wiped on every redeploy, so local storage is for development only.
6. **Embeddings:** Voyage AI (`voyage-3.5-lite`), the embeddings provider Anthropic recommends. It turns resume and job text into vectors that are stored in pgvector for similarity search. It has a free token allowance, then very low per-token pricing.
7. **Match result:** a score from 0 to 100, plus missing skills, almost-matched skills (e.g. "React" vs "React Native"), missing keywords, the most suitable headline or job title for this job, and how well the location fits (remote, hybrid, on-site, distance).
8. **Dashboard:** built early in Phase 2 and extended as each feature or page ships. The PRD line has been reworded to say this.
9. **CI:** GitHub Actions. The git repository was initialized on 2026-11-15.

### 1.4 Follow-up decisions (2026-11-15)

1. Mail provider: Gmail first, with read-only access.
2. Reminders: 7 days after applying with no reply, shown in the app. This is a default and can be changed.
3. Location preferences: stored on the profile (location, remote OK).
4. Schema fixes in 1.2: approved. Pasted jobs become `jobs` rows with source "manual". Suggestions are generated on request. Drafts are `applications` rows with status "draft".
5. Pasted jobs before the AI module exists: `POST /jobs/match` stores the job and returns a placeholder (`match: null` plus a message). The real score arrives with Phase 5. Pasted jobs carry `created_by_profile_id` so only the user who pasted a job can see it.
9. Location and category filters (2026-11-15): job_locations table + jobs.category, GET /jobs/facets; see ARCHITECTURE.md "Job location and category filters".
8. Job filters (2026-11-15): GET /jobs gets q, source, remoteType, location, postedWithin and offset; a /jobs page uses them (ARCHITECTURE.md "Job filters").
7. Scraper (2026-11-15): Greenhouse and Lever first via a JobSource interface, hourly, boards configured by SCRAPER_BOARDS; see ARCHITECTURE.md "Job scraper".
6. Resume limit: a profile stores at most 2 uploaded resumes. Each upload is kept and the newest becomes the base resume; a third upload is refused (409) until the user deletes one (`DELETE /resumes/{id}`). Tailored copies do not count toward the 2.

### 1.5 Planned module layout (from CODE_STYLE.md)

```
backend/   src/main/java/.../{auth,profiles,resumes,jobs,applications,ai,agent,common}
           each module: controller, service, repository
           src/main/resources/db/migration   (Flyway)
frontend/  src/{pages,components,services,utils}
```

### 1.6 Next steps

1. Done: decisions made and docs updated.
2. Write the first Flyway migrations from DATABASE.md.
3. Done: backend auth module, run against local Postgres, frontend switched off the auth mock (`VITE_USE_MOCK_AUTH=false`).
4. Done: dashboard (early version) and profile page; profiles migration and `GET/PUT /profile`.
5. Done: resume upload (`resumes` migration, `POST /resumes`, local `StorageService`, text extraction, upload page and dashboard card).
6. Done: jobs (`POST /jobs/match`, `GET /jobs` with filters, match page, scraper) and applications (V6 migration, `POST/GET/PUT /applications`, real dashboard table, "Track this job").
7. Next: Phase 5 AI (migration, Testcontainers, real match), email tracking, CI. See docs/handoff.md "Next steps".
