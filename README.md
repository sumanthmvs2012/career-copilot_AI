# AI Job Application and Career Copilot

A web app for job seekers. Upload a resume, see how well it matches a job, tailor it for that job, get interview questions, track applications on a dashboard, and follow employer replies by email.

**Stack:** React + Vite (frontend), Spring Boot 4.1 on Java 21 (backend, modular monolith), PostgreSQL with pgvector (Docker locally), Flyway migrations. AI features are rule-based for now (no AI keys yet); Claude and Voyage embeddings come later.

Source of truth: `PRD.md`, `ARCHITECTURE.md`, `DATABASE.md`, `DESIGN_SYSTEM.md`, `SECURITY.md`, `CODE_STYLE.md`, `TESTING.md`, plus `docs/plan.md` (phases and decisions) and `docs/handoff.md` (detailed session notes). `CLAUDE.md` and `AGENTS.md` hold the working rules.

## Run it locally

1. Start Docker Desktop. In `backend/`: `docker compose up -d`, then `mvn spring-boot:run` (http://localhost:8080).
2. In `frontend/`: `npm run dev` (http://localhost:5173).
3. `backend/.env` and `frontend/.env` are local and gitignored; copy from the `.env.example` files.

Tests: `mvn -q test` in `backend/`. In `frontend/` run `npm test`, `npm run lint` and `npm run build` from PowerShell (vitest fails under Git Bash).

## What is built

| Area | Features |
|------|----------|
| Accounts | Sign up, log in (JWT), BCrypt, rate limiting, ownership checks on every endpoint |
| Profile | Name, headline, location, remote OK, auto-submit setting (off by default) |
| Resumes | PDF/DOCX upload (5 MB), skills extracted, up to 2 uploads (newest is the base), delete, tailored copies per job |
| Jobs | Hourly scraper (Greenhouse, Lever), filters by category, country and state (each narrows the others), pasted jobs kept private |
| Matching | Keyword score, missing and near-matched skills, keywords, suggested headline, location fit; matches stored |
| Tailoring | Rule-based tailored copy (never adds claims), suggestions, copy and download text |
| Interview questions | 20 to 30 per job from the job and your resume, stored, regenerate on request |
| Applications | Track a job, dashboard table, colour-coded status dropdown, cover letter, Edit & submit (marks applied in the app) |
| Email tracking | Gmail read-only OAuth, encrypted tokens, reply classification (rejection, interview, offer, other), 7-day reminders |
| UI | Dashboard hero with stats, accent cards, job list rows with category pill and inline actions, suggestion cards, score gauge |

## Progress, date by date

### 2026-11-15

- **Planning.** Wrote the plan with nine phases, a requirements map and a list of gaps. Decisions: the agent asks before submitting (auto-submit is opt-in), Gmail read-only for replies, Greenhouse, Lever and Ashby job sources first, Voyage embeddings later, resume storage on local disk behind a `StorageService` (Cloudflare R2 before launch), GitHub Actions for CI. Docs updated to match.
- **Frontend start.** Login and sign-up pages (token in memory, copied to sessionStorage), then the dashboard, profile page and resume page, with loading, empty, error and success states.
- **Backend start.** Auth, profiles and resumes modules, Flyway V1 to V3, JWT, rate limiting, a global error handler and CORS. Run against real Postgres (pgvector in Docker) and checked in the browser.
- **Jobs.** V4 migration, pasted-job saving, `GET /jobs` with filters, the match page, and the hourly scraper (Greenhouse and Lever; about 400 postings stored from Stripe and Palantir). V5 added job categories and locations, with a location parser and cascading facets.
- **Applications.** V6 migration, `POST/GET/PUT /applications`, a "Track this job" button and a status dropdown on the dashboard. Verified in the browser.
- **GitHub.** Repo `career-copilot_AI` created. Rule set by you: work goes to `feature_v1`; `main` only when you say so. An early push to `main` was undone and `main` emptied, then filled again when you asked.
- **Filters and matching.** Cascading Category, Country, State and City filters; keyword skill extraction (about 300 skills); match storing (V7); match UI. Decision: keyword matching, no AI keys. Commit `88905b0`.

### 2026-11-16

- **Tailoring.** `POST /resumes/{id}/tailor` and suggestions, a tailored resume page, a "Tailored copies" list. Commit `a9c4060`.
- **Interview questions, submit and email tracking.** V8 and V9 migrations; rule-based interview questions; `POST /applications/{id}/submit`; Gmail OAuth behind a mail provider interface, encrypted token storage, reply sync and classifier, reminders; frontend interview page, submit panel and mailbox card. Smoke-tested over HTTP on real Postgres. Commit `69bee88`.
- **UI refresh.** Dashboard banner with stats, accent cards, Latest jobs as list rows with the category pill and actions on the right, friendlier suggestion cards, a match score gauge. Filters cut to Category, Country and State (City removed); resume count now excludes tailored copies; long file names truncate; demo applications data for the dashboard. `CLAUDE.md` added. Commit `f7b270b`.
- **Status dropdown.** The status dropdown is now the colour-coded status display, with coloured options; the separate badge and label were removed. Commit `78c1f67`.

## Test status (last run by the build agents)

Backend 669 tests, frontend 411 tests (36 files); lint and build clean. Not re-run for this README.

## Not done yet

- A live run with real Voyage and Anthropic keys. Embedding matching and the Claude-written interview questions, suggestions and tailoring are built and tested, but only with fakes. Without keys they use the rule-based versions.
- The hourly agent that writes application drafts for new postings (match score 70 or more).
- Real submission to employers (submit only records "applied" in the app; auto-submit exists in code only).
- Gmail has never been run against real Google; it needs `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI` and `MAIL_TOKEN_KEY`.
- More job sources (Ashby, Workable, SmartRecruiters) and more companies.
- GitHub Actions CI, deployment (Vercel, Render, Neon), Sentry, health checks, Cloudflare R2 storage.

## Known gaps

- Two simultaneous requests can store the interview-question list twice, or error on a duplicate match save.
- Some "missing keywords" are weak ("senior", "developer"); a job with no recognised skills scores 0 with no explanation.
- A 404 from tailor or suggestions shows the "upload a resume" message.
- `AGENTS.md` says the docs are in `docs/`, but most are at the repo root.
- Assumptions to confirm: 8-character minimum password, 200-character field limits, the 2-resume cap counts uploads only.
- Match, suggestions and tailored-resume screens and phone width were not re-checked in the browser after the redesign.
- `frontend/.env` on the developer machine has `VITE_USE_MOCK_APPLICATIONS=true` (demo applications); set it to `false` to see real ones.

## Next up

1. Browser-check the remaining screens and phone width.
2. GitHub Actions CI.
3. Add Voyage and Anthropic keys and try embeddings and the Claude-written features for real.
4. The hourly draft agent.
5. Live Gmail run (needs Google credentials).
6. Deployment and monitoring.
