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
- `/jobs` page with filters (search, source, remote type, location text, posted within, scope) and dashboard Latest-jobs card with compact Category/Country/State filters (shared `JobFilters`, options from `GET /jobs/facets`).
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

## Session 5 addendum (2026-11-15): cascading filters + keyword matching
- Filters: `GET /jobs/facets` takes every `GET /jobs` filter and computes each list with all other filters applied (category narrows countries; country narrows states and cities; state narrows cities). `LocationParser` normalises aliases and drops junk; `JobLocationReparser` re-derives `job_locations` for existing jobs at startup (`LOCATIONS_REPARSE_ON_STARTUP`, default true; 27 of 401 jobs rewritten live). Frontend `JobFilters` refetches on every change, resets invalid children, disables state/city until a country is chosen.
- Matching (decision: keyword matching, no AI keys, no embeddings yet): `V7__add_skills_and_create_job_matches.sql` (`resumes.skills`, `jobs.skills` as JSON text; `job_matches`), `matches/` module (`SkillExtractor` with `skills.json` of ~300 skills, `KeywordFinder`, `LocationFitCalculator`, `MatchService`), `POST/GET /jobs/{id}/match`, `POST /jobs/match` now returns the match when a base resume exists. Resume responses carry `skills`. Frontend: `MatchResult` component, "Match with my resume" on job cards, skill chips on `/resume`. Verified in the browser on real Postgres (pasted Java job: score 0 against a Python/SQL test resume, missing skills, near match "PostgreSQL vs SQL", headline, location fit "unknown").
- Counts: backend 533 tests, frontend 289 tests (24 files); lint and build pass.
- Known gaps: a job with no recognised skills scores 0 with no explanatory note field; two simultaneous match POSTs for one job can hit the unique constraint (500); missing keywords need 2+ occurrences; the frontend `matchJob` was renamed `matchPastedJob` for pasted jobs.
- Next: resume tailoring and suggestions, interview questions, email tracking, Claude + Voyage embeddings (replace the keyword internals, same result shape), CI. Commit and push to `feature_v1` only when the user asks.

## Session 6 addendum (2026-11-15): resume tailoring (rule-based) + GitHub main
- Decision (user): no AI key, so tailoring is rule-based. `POST /resumes/{id}/tailor` (body `{jobId}`) saves a copy as `resumes/<profileId>/<uuid>.txt` with `base_resume_id` and `job_id`: first line = job title (else profile headline), then `Relevant skills: ...` (only skills the resume already has that the job asks for, in job order), then the original text unchanged. Never adds claims. Re-tailoring the same resume+job replaces the copy. `POST /resumes/{id}/suggestions?jobId=` returns advice (add_skill, near_match, add_keyword, headline, location, general); nothing is added automatically. Tailored copies don't count toward the 2-resume limit and are never the base for matching; deleting an upload deletes its copies. No schema change.
- Backend: `ResumeTailorService`, `ResumeSuggestionService`, `TailoredTextBuilder`, `ResumeJobLabels`, `MatchService.analyze` (match data without writing job_matches); resume responses gain `jobId`, `jobTitle`, `company`; missing request params now return 400.
- Frontend: "Tailor my resume for this job" and "Get suggestions" on job cards, `/resume/:id` page (text, suggestions fetched via the suggestions endpoint, Copy text, Download .txt, two-step Delete), "Tailored copies" section on `/resume`.
- Verified in the browser on real Postgres: Data Analyst job against a Python/SQL resume gave "Relevant skills: SQL, Python", suggestions survive a reload, single Delete button.
- Counts: backend 571 tests, frontend 320 tests (25 files); lint and build pass.
- GitHub: remote `main` and `feature_v1` were both set to commit 88905b0 on the user's request ("push to feature and to main"). Push rule: `feature_v1` by default, `main` only when the user says so in that request. Local `main` is stale.
- Next: interview questions, email tracking, Claude + Voyage embeddings (keyword internals replaced, same shapes), `POST /applications/{id}/submit`, CI.

## Session 7 frontend
- Interview questions: `services/interviewService.js` (+ `mockInterview.js`, flag `VITE_USE_MOCK_INTERVIEW`), page `/jobs/:id/interview` (grouped by category, Copy all as numbered plain text, Regenerate, hint to upload a resume when none), "Interview questions" button on job cards.
- Submit application: `submitApplication(id, coverLetter)` in `applicationsService.js` (+ mock); dashboard draft rows get "Edit & submit" opening `SubmitApplicationPanel` (cover letter, 10000 char counter, Save, confirm step, 409 "Only a draft can be submitted.").
- Email tracking: `services/mailService.js` (+ `mockMail.js`, flag `VITE_USE_MOCK_MAIL`), dashboard `MailboxCard` (connect, Check now, two-step Disconnect, Reminders with Dismiss), `?mail=connected|error` read then removed from the URL. Applications table shows the reply classification badge and an Actions column.
- Frontend only; coded against the ARCHITECTURE.md contracts, not yet run against the real backend. Mock flags are dev only (`.env.example` lists them).

## Session 7 backend (2026-11-16): submit, interview questions, Gmail tracking, reminders
- Built (V8 and V9 migrations were already present, unchanged): `POST /applications/{id}/submit` (+ internal `submitAuto`, gated by `autoSubmitEnabled`), `interviews/` module (`GET/POST /jobs/{id}/interview-questions`, rule-based `InterviewQuestionGenerator`, 20 to 30 unique questions, stored per profile and job), `mail/` module (`MailProvider` + `GmailMailProvider`, `TokenCipher` AES-GCM, signed OAuth state, `EmailClassifier`, `MailSyncService` + hourly scheduler, `ReminderService` + scheduler, `/mail-connections`, `/applications/{id}/emails`, `/reminders`). `ApplicationResponse` gained `emailClassification`, and `emailSent`/`emailSentAt` now come from `email_logs` (one batch query for the list).
- Config (all optional, see `backend/.env.example`): `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI`, `MAIL_TOKEN_KEY` (base64 of 32 bytes), `FRONTEND_URL`, `MAIL_SYNC_ENABLED` (off by default), `REMINDER_DAYS` (7), `REMINDERS_ENABLED` (on by default, off in tests). The mailbox flow has only been tested with a fake provider and a mocked HTTP server; it has not run against real Google yet (needs a Google Cloud OAuth client with the callback URL as redirect URI).
- Security: the callback path `GET /mail-connections/callback` is the only new permitAll route. Tokens, snippets, senders and bodies are never stored or logged; exceptions from the provider carry fixed messages.
- Known gaps: two simultaneous first GETs of interview questions for one job could store the list twice (no unique constraint); mail matching is by company name or website domain only, so jobs without a company are never matched; one scan reads at most the 100 newest messages.
- Tests: 669 backend tests pass (`mvn -q test`), `mvn package` passes.


## Session 8 UI refresh (2026-11-16)
- Frontend only, no API or data changes. Dashboard has a gradient hero (greeting, Applications / Replies / Latest jobs / Resumes tiles), gradient-barred cards with icons, a teal Mailbox card, nicer empty states and a polished Applications table. 3 columns from 1024px; mailbox moved above Latest jobs.
- Latest jobs and `/jobs` are list rows: `JobDetails variant="row"` (title h3 + category pill, details, actions on the right, one panel under the row for match, suggestions, tailor errors and description). Default card variant is unchanged (used by `/match`). New: `Icon`, `EmptyState`, `utils/clipboard.js`, `styles/refresh.css`; `TrackJobButton` takes `label` and `small`.
- `ResumeSuggestions` is grouped cards (Add a skill, Close match, Keywords to include, Headline, Location, Tips) with priority words and per-item Copy; `MatchResult` uses a circular gauge. DESIGN_SYSTEM.md Part five documents it.
- Tests: 407 frontend tests (34 files) pass; lint and build pass. Not yet browser-checked. Nothing committed.

## Session 8 fixes (2026-11-16)
- Frontend only. Resume count (dashboard "N of 2" and the Resumes tile) counts uploads only, not tailored copies; long file names truncate with a title tooltip.
- Latest jobs rows: actions sit under the row text (one or two compact lines, icon + short label for Suggestions, Interview, Track, View details); location shows the first 2 places without the country plus "+N more" (full list in the title attribute).
- City filter removed from the UI (`JobFilters`, `/jobs` URL params, `listJobs`/`getJobFacets` no longer send `city`). Exactly Category, Country, State. State is enabled as soon as a country is chosen ("Loading states..." while facets load). The backend still accepts `city` and returns `cities`; the frontend ignores them.
- Checked against the real backend: `GET /jobs/facets?scope=scraped&country=United States` returns states (New York 78, District of Columbia 44, California 31, Washington 15, ...). Tests: 410 frontend tests pass; lint and build pass.
- Applications table: the status badge and visible "Change status for ..." label are gone. `StatusSelect` is the status display: a pill-shaped select coloured by the current status (same --status-* pairs as `StatusBadge`), a leading dot, coloured options, a screen-reader-only label, and an immediate colour change on pick (reverts if the save fails).

## Session 9 agent (2026-11-16): hourly draft agent (backend)
- Built `agent/` module: `DraftAgentService` (`run()` for all profiles with a base resume, `runForUser(userId)` for one), `CoverLetterTemplate` (rule-based, no invented claims), `DraftAgentScheduler` (only when `AGENT_ENABLED=true`, cron `AGENT_CRON`, default hourly at :15), `AgentController` `POST /agent/run` -> `{considered, draftsCreated}` (404 no profile, 409 "Upload a resume first." no base resume). Score threshold `AGENT_MIN_SCORE` (default 70). Contract is in ARCHITECTURE.md "Draft agent".
- Drafts are `applications` rows with status "draft" and `resume_id` = base resume. The agent never submits and never calls `submitAuto`, even with auto-submit on (no job-board integration yet; TODO in `DraftAgentService`).
- Schema: `V10__add_profiles_last_agent_run_at.sql` (`profiles.last_agent_run_at`, nullable); DATABASE.md updated. `.env.example` lists `AGENT_ENABLED`, `AGENT_MIN_SCORE`, `AGENT_CRON` (commented).
- Tests: 692 backend tests pass (23 new: `DraftAgentServiceTest`, `CoverLetterTemplateTest`, `AgentControllerIntegrationTest`).
- Known gaps: no frontend for the agent yet (no "Run now" button or draft badge); a re-scraped job is never re-considered (window uses `jobs.created_at`); jobs with no recognised skills score 0 and never get a draft.

## Session 9 frontend (2026-11-16)
- Dashboard: `DraftAgentPanel` in the Applications card ("Find new matches", one-line explanation, aria-live result "Checked N new jobs, created M drafts", 409 -> link to /resume, 404 -> link to /profile, other errors generic, status-only console log; refreshes the applications list). `services/agentService.js` (`runAgent`, POST /agent/run) + `mockAgent.js` (flag `VITE_USE_MOCK_AGENT`, dev only, listed in `.env.example`). Agent drafts are ordinary draft rows, so Edit & submit works unchanged; no "made by agent" marker (the API has no flag).
- `MatchResult` shows an optional `match.note` in an info Alert (new `.alert--info` style).
- Tailor and suggestions 404/409 on job rows now show the server's `message` when present, else the old "Upload a resume first to tailor it." text.
- Tests: 427 frontend tests (38 files) pass; lint and build pass. Not run against the real backend yet.

## Session 9 match quality (2026-11-16)
- missingKeywords: words from the job title and company name are excluded and the stopword list has many generic job/business words; still 2+ occurrences. A skill equal to the company name is dropped (`matches/CompanySkills`, used by match, suggestions via the analysis, and interview questions).
- Match response gains `note` (null, or "No known skills were found in this job description, so the score cannot be calculated." when no skills; score 0). Derived from the stored lists, no schema change. Frontend can show it.
- Races: `MatchWriter` stores the match in its own transaction; a DataIntegrityViolation on a simultaneous POST becomes an update of the other row (`matchJob` is no longer one big transaction). `InterviewQuestionWriter` + `V11__interview_questions_unique_position.sql` (unique profile, job, position): the losing first GET returns the stored list.
- 404s: tailor/suggestions already returned "Resume not found." vs "Job not found."; documented in ARCHITECTURE.md so the frontend can branch on the message.
- Constructor changes: `MatchService` (+CompanyRepository, MatchWriter), `InterviewQuestionService` (+InterviewQuestionWriter), `MatchResponse` (+note). Tests: 703 backend tests pass.

