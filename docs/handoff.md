# Session handoff: start here

To continue in a new Claude session (VS Code, terminal or the desktop app), paste this:

> Read AGENTS.md, docs/handoff.md and docs/plan.md, then continue the project from "Next steps" in handoff.md.

Last updated: 2026-11-15 (session 4: Applications done; see "Session 4 addendum" at the end).

## Project

AI Job Application and Career Copilot, a web app for job seekers. Core features:
1. Upload a resume and extract skills.
2. Match it against jobs (score, missing and almost-matched skills, keywords, best headline, location fit).
3. Suggest resume improvements.
4. Track applications on a dashboard.
5. Generate interview questions.
6. Run an hourly agent that collects postings and writes application drafts.

Stack: React + Vite (frontend), Spring Boot 4.1 / Java 21 (backend), PostgreSQL + pgvector on Neon, Claude API, Voyage AI embeddings. Hosting is Vercel, Render and Neon.

Source of truth: the root docs (PRD, ARCHITECTURE, DATABASE, DESIGN_SYSTEM, SECURITY, CODE_STYLE, TESTING) plus docs/plan.md (nine phases and every decision, sections 1.3 and 1.4).

## Working rules (from the user)

- **Never commit or push unless the user says so.** The user will create a personal GitHub repo and say when.
- Follow AGENTS.md: read the docs, plan, then code. Run the tests, lint and build afterwards, and update the docs.
- Don't invent business rules; ask when something is unclear.
- Run only one Claude session at a time on the same files.

## Key decisions already made

- The agent asks before submitting an application. If the user turns on auto-submit, it submits without asking, but only where a job board allows applications through an API.
- Drafts are written only for postings with a match score of 70 or more.
- Job sources: public APIs, starting with Greenhouse, Lever and Ashby. No LinkedIn or Indeed scraping.
- Email: read-only Gmail access to track replies (rejection, interview, offer), plus in-app reminders 7 days after applying with no reply.
- Resume storage: local disk in development, Cloudflare R2 before launch.
- The dashboard is built early and grows with each feature.
- CI: GitHub Actions.

## Current state

- Git repo on `main`. One commit (`7514ac1`: docs, plan, login page). Everything below is **uncommitted**.
- **Frontend** (`frontend/`):
  - Login, sign-up, dashboard, profile and resume pages are done. `/dashboard`, `/profile` and `/resume` redirect to `/login` without a token (`components/RequireAuth.jsx`); a 401 clears the token and redirects.
  - Dashboard: profile card (links to `/profile` when there is no profile yet), resume card (links to `/resume`; shows file name and date once uploaded) and an Applications table with the PRD columns (Job, Status, Applied, Email sent, When). The table reads the real `GET /applications` through `services/applicationsService.js` (mock only when `VITE_USE_MOCK_APPLICATIONS=true`, dev only).
  - Resume page (`/resume`): file input (PDF/DOCX, 5 MB, checked client-side first), upload/replace, shows "Text extracted" when `textLength > 0`. `apiClient.js` has an `upload(path, formData)` helper that leaves Content-Type to the browser.
  - Profile page: full name (required), headline, location, "Open to remote work", "Auto-submit applications" (off by default, with a note). Maps `fieldErrors` from a 400 onto the fields.
  - `apiClient.js` adds `Authorization: Bearer <token>` whenever a token exists and exports `get`, `post`, `put`.
  - The token is kept in memory and copied to sessionStorage.
  - `frontend/.env` currently has `VITE_USE_MOCK_AUTH=false` (real backend). Mocks: `VITE_USE_MOCK_AUTH=true` (demo@example.com / password123), `VITE_USE_MOCK_PROFILE=true` and `VITE_USE_MOCK_RESUMES=true` (in-memory, dev only). See `.env.example`.
  - 116 tests pass. Lint and build pass.
  - Run it with `npm run dev`, then open http://localhost:5173.
- **Backend** (`backend/`):
  - Auth module (`POST /auth/signup`, `POST /auth/login`) returns `{token}`.
  - Profiles module (`profiles/`): `GET /profile` (404 "Create your profile first." until created) and `PUT /profile` (creates or updates). The user id comes only from the JWT subject, so users can only reach their own row. Blank headline/location are stored as null. Request booleans are `Boolean` (missing = false) because Jackson rejects a missing primitive in a record.
  - Resumes module (`resumes/` + `common/storage/`): `POST /resumes` (multipart `file`, PDF/DOCX by extension and magic bytes, 5 MB, text via PDFBox/POI; needs a profile, else 404), `GET /resumes`, `GET /resumes/{id}` (owner only). One base resume per profile: a second upload replaces it in place (same id) and deletes the old file. `StorageService` → `LocalDiskStorageService`, root from `RESUME_STORAGE_DIR` (default `./storage`, gitignored). Server-generated keys `resumes/<profileId>/<uuid>.<ext>`.
  - JWT (HS256), BCrypt, rate limiting (10 per minute per IP), a global error handler with `{status, message}`, CORS for localhost:5173, Flyway `V1__create_users.sql`, `V2__create_profiles.sql`, `V3__create_resumes.sql`, health check.
  - 62 tests pass (H2 in-memory). `mvn package` passes.
  - **Verified against real Postgres on 2026-11-15**: `docker compose up -d` (pgvector/pg17), V1–V3 applied, and signup/login/profile/resume endpoints exercised with curl (upload, replace, .txt and fake-.pdf rejected, cross-user 404), including CORS preflight from http://localhost:5173. `backend/.env` exists locally (gitignored) with generated secrets.
  - **Browser click-through done on 2026-11-15** (Claude in Chrome): sign-up → dashboard → profile save → resume upload → dashboard shows profile and resume cards.
  - Deferred by decision (2026-11-15): `resumes.skills` (jsonb) and `resumes.embedding` (vector 1024) arrive with the Phase 5 AI migration, when the backend tests move from H2 to Testcontainers with pgvector; `resumes.job_id` arrives with the jobs migration so it can carry its foreign key.
- **Agents** in `.claude/agents/`:
  - `frontend-builder` (owns frontend/)
  - `backend-builder` (owns backend Java code)
  - `database-builder` (owns migrations and DATABASE.md)
- Assumptions to confirm later:
  - The minimum password length is 8, because SECURITY.md has no password policy yet.
  - The login response shape is `{token}`.

## Session 2 outcome (2026-11-15): all three in-flight items are DONE

Verified after the session resumed: backend 89 tests, frontend 167 tests, lint and build pass; V4 applied on Postgres; curl-checked POST/GET /jobs, 404 without profile, 400 on short description, two resumes then 409 on a third, delete frees a slot. Dashboard restyled (gradient banner, Avatar, StatusBadge, per-card accents; DESIGN_SYSTEM.md Part four records the extension of the single-accent rule). Not yet done: browser check of /resume, /match and the new dashboard look. Original in-flight notes (historical):

Three builder agents were still running when the session ended. Their work may be complete, partial or absent on disk. Start by running `git status`, then from `backend/` `mvn test` and from `frontend/` `npm test && npm run lint && npm run build`, and compare with the expected end state below. Finish whatever is missing (same agents, same contracts in ARCHITECTURE.md), then restart the backend so V4 runs on Postgres and re-check in the browser.

1. **DONE — backend two-resume limit + delete** (reported complete, 75 backend tests passing, `mvn package` ok): `ResumeService.upload` keeps up to 2 uploaded resumes (rows with `base_resume_id` null), newest becomes base, a third upload → `ResumeLimitReachedException` → 409 "You already have 2 resumes. Delete one to upload another."; `DELETE /resumes/{id}` → 204 with base promotion and file removal; `ResumeRepository` gained `countByProfileIdAndBaseResumeIdIsNull` and `findFirstByProfileIdAndBaseResumeIdIsNullOrderByCreatedAtDesc`. Not yet exercised against Postgres or in the browser.
2. **backend-builder: jobs module** (contract: ARCHITECTURE.md "Match a pasted job" / "List jobs"; entities `jobs/Company.java`, `jobs/Job.java` and `V4__create_companies_and_jobs.sql` already exist). Expected: `jobs/JobController` (`POST /jobs/match` → 201 `{job, match: null, message}`, `GET /jobs`), `JobService`, `JobRepository`, `CompanyRepository`, `JobMatchRequest`, `JobResponse`, `JobMatchResponse`, `JobServiceTest`, `JobControllerIntegrationTest`. Pasted jobs carry `created_by_profile_id` and are only listed for their creator. It was told not to edit `GlobalExceptionHandler.java`.
3. **frontend-builder: two-resume UI**. Expected: `apiClient.js` has `del(path)`; `resumeService.js` has `deleteResume(id)`; `mockResume.js` keeps 2 and returns 409 on a third; `ResumePage.jsx` lists up to 2 resumes ("n of 2", Base badge, inline two-step Delete, upload disabled at 2, shows a 409 message); dashboard resume card shows "n of 2 resumes" and a "Manage resumes" link; tests updated. Before this change the frontend had 147 passing tests (14 files).

Already landed before the session ended (verified): V4 migration + `Company`/`Job` entities + `Resume.jobId` (65 backend tests on H2); the `/match` page, `jobService.js`, `mockJobs.js`, `TextArea.jsx`, dashboard Jobs card (147 frontend tests). The running backend on :8080 was started **before** V4 and the jobs/resume changes, so it must be restarted (`mvn spring-boot:run` from `backend/`). Postgres has V1–V3 applied; V4 applies on the next start.

Decisions made this session (also in docs/plan.md 1.4 items 5–6): pasted jobs are stored now and return a placeholder until Phase 5; a profile stores at most 2 uploaded resumes, newest is base, third upload refused, delete endpoint added.

## Next steps

To run everything locally: start Docker Desktop, then in `backend/` run `docker compose up -d` and `mvn spring-boot:run`; in `frontend/` run `npm run dev` and open http://localhost:5173. `backend/.env` already exists on this machine.

Run frontend tests from PowerShell (`npm test`); under Git Bash vitest fails with "failed to find the current suite" (path casing).

1. Phase 5 prep: the Phase 5 migration adds `resumes.skills`, `resumes.embedding` and `jobs.embedding`, and moves the backend tests to Testcontainers with pgvector (Docker is available on this machine). Then the real `POST /jobs/match` result replaces the placeholder (score, missing and almost-matched skills, keywords, headline, location fit).
2. Email tracking: `email_logs` table and Gmail read-only OAuth; the dashboard "Email sent / When" columns are false/null until then.
3. Resume tailoring, suggestions, interview questions, and the agent that writes drafts (drafts are applications rows with status "draft").
4. Set up GitHub Actions CI on `feature_v1`.
5. Fix AGENTS.md Part two wording (root docs are at the repo root, not in `docs/`).

## Session 3 addendum (2026-11-15): job scraper
- Built `jobs/scraper/` (JobSource, GreenhouseJobSource, LeverJobSource, JobScraperService, hourly JobScraperScheduler, off unless `SCRAPER_ENABLED=true`). `backend/.env` (local, gitignored) now has `SCRAPER_ENABLED=true` and `SCRAPER_BOARDS=greenhouse:stripe,lever:palantir`. Live run stored 400 postings (200 per board). Idempotent re-run verified by tests only, not yet live.
- `GET /jobs?scope=pasted|scraped&limit=N`; dashboard has a "Latest jobs" card (pink accent) with company, source, "Posted N days ago", description toggle and "View posting" link. Pasted-jobs lists use `scope=pasted`.
- Job details show description/postedAt/source; the match score is still "coming soon" (Phase 5).
- Counts: backend 117 tests, frontend 194 tests; lint and build pass.
- Open: Ashby/Workable/SmartRecruiters sources; which companies to scan (only Stripe and Palantir so far); the dashboard Latest-jobs list shows only the 10 newest (all Stripe so far); postings removed from a board are not deleted; no admin "scan now" trigger.
- Note: someone edited AGENTS.md Part two (now says "read the files in the docs folder"); the root docs live at the repo root, so this wording may need fixing.

## Session 3 addendum 2 (2026-11-15): filters, locations, categories
- `/jobs` page with filters (search, source, remote type, location text, posted within, scope) and dashboard Latest-jobs card with compact Category/Country/State/City filters (shared `JobFilters`, options from `GET /jobs/facets`).
- V5 applied on real Postgres: `jobs.category` + `job_locations`. Scraper fills both on every run (verified live: second run created 0, updated 200 per board). `LocationParser` and `CategoryClassifier` in `jobs/scraper/` are best effort.
- Known parser limits: "City, X, Country" reads X as a state (e.g. Atlanta shows up as a US state); regions outside US/CA need a following country; "Bay Area" becomes city "Bay"; rows with no parsable location only match the free-text location filter. Category from department first (Greenhouse departments, Lever team), then title.
- Dashboard: removed the small pasted-jobs card (Latest jobs links to /jobs and /match); Applications table shows 10 dummy rows when `VITE_USE_MOCK_APPLICATIONS=true` (frontend/.env, dev only, "Demo data" badge).
- Counts: backend 395 tests, frontend 244 tests; lint and build pass. Nothing committed.

## Session 4 addendum (2026-11-15): Applications + GitHub
- Browser-checked on real Postgres: dashboard look, `/resume` (two uploads, upload disabled at 2, delete frees a slot), `/match` (job saved with the placeholder note).
- Applications built: `V6__create_applications.sql` (UNIQUE profile+job, status CHECK, submitted_by user/auto), `applications/` module (`POST/GET /applications`, `PUT /applications/{id}`, 409 "You already track this job.", ownership via the JWT subject, no N+1), a "Track this job" button on every job card, and a status dropdown per dashboard row. Verified in the browser: tracking a pasted job, then changing it to Applied, stored `applied` with a submitted date.
- Counts: backend 425 tests, frontend 255 tests (23 files); lint and build pass.
- `frontend/.env` (local, gitignored) now has `VITE_USE_MOCK_APPLICATIONS=false`; restart Vite after changing env values.
- GitHub: remote `origin` = https://github.com/sumanthmvs2012/career-copilot_AI.git. **Push only to `feature_v1`, never `main`** (remote `main` is a single empty commit on purpose). Commit and push only when the user says so.
- Not done yet: `POST /applications/{id}/submit`, cover letters in the UI, email tracking.
