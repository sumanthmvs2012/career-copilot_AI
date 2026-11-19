# CLAUDE.md

Read this first in every session. It keeps the work on goal. `AGENTS.md` has the same core rules; if they ever differ, point out the conflict and ask.

## The goal

Build the **AI Job Application and Career Copilot**: a web app that helps job seekers
1. upload a resume and pull out its skills,
2. match it against jobs (score, missing skills, near matches, keywords, location fit),
3. tailor the resume and get suggestions for a specific job,
4. track applications on a dashboard (draft, applied, interviewing, offer, rejected, withdrawn),
5. get interview questions for a job,
6. track employer replies by email (Gmail, read-only) and get reminders,
7. later: an hourly agent that writes application drafts for new postings (match score 70+).

Do not add features that are not in `PRD.md`, `docs/plan.md` or a user request. Do not invent business requirements. If requirements conflict or are unclear, ask instead of guessing.

## Source of truth (read before any significant change)

`PRD.md`, `ARCHITECTURE.md`, `DATABASE.md`, `DESIGN_SYSTEM.md`, `SECURITY.md`, `CODE_STYLE.md`, `TESTING.md` at the repo root, plus `docs/plan.md` (phases and decisions) and `docs/handoff.md` (what is built, known gaps, next steps). Do not contradict them without pointing out the conflict first. When behaviour changes, update the docs in the same task.

## Stack

- Frontend: React + Vite, react-router, Vitest + React Testing Library (`frontend/`).
- Backend: Spring Boot 4.1 on Java 21, modular monolith by feature (`backend/src/main/java/com/careercopilot/{auth,profiles,resumes,jobs,matches,applications,interviews,mail,common}`), each module with controller, service, repository.
- Database: PostgreSQL + pgvector (Docker, `backend/compose.yaml`), Flyway migrations V1 to V13 in `backend/src/main/resources/db/migration` (V12 pgvector in `db/migration-pg`, PostgreSQL only). Tests run on H2 in PostgreSQL mode.
- AI: optional `ai/` module. With `VOYAGE_API_KEY` set, matching blends keyword and embedding (pgvector) scores. Without keys everything is rule-based. The Anthropic client exists but no feature uses it yet. No keys are set locally yet.

## Rules

1. Understand existing code before changing it. Reuse existing components, services and utilities. Do not change unrelated files.
2. Before a non-trivial task: read the relevant docs and code, list the affected files, write a short plan, then code.
3. After coding: run the tests, lint and build, then review the changed files. A task is done only when requirements are met, tests pass, the build succeeds, no unintended files changed, security rules hold, and the docs are updated.
4. Security (`SECURITY.md`): every endpoint checks ownership from the JWT subject; never expose secrets; never log tokens, resume text, email bodies or snippets; mail refresh tokens are stored encrypted only; auto-submit is off by default and only the user can turn it on. Never commit `.env` files, `target/`, `node_modules/` or uploaded files.
5. Every screen has loading, empty, error and success states, works on phones, and meets WCAG AA. Status is never shown by colour alone.
6. Dev mocks (`VITE_USE_MOCK_*`) are for development only and must never be the default.
7. Tests are part of the work: add or update tests for every change. Do not delete tests to make them pass.

## Git rules (set by the user)

- **Standing rule (since 2026-11-16): after a finished task, commit and push directly, without asking.** Plain pushes only; never force-push or rewrite history unless asked.
- Work on `feature_v1` and push it, then push the same commit to `main` (`git push origin feature_v1:main`). Name the branch in every `git push`. Check staged files for secrets first.
- Remote: `origin` = the user's `career-copilot_AI` GitHub repo. End commit messages with the Co-Authored-By line given by the harness.

## How to work here

- Run only one Claude session at a time. Subagents in `.claude/agents/` (frontend-builder, backend-builder, database-builder) may run in the background; only one agent runs Maven at a time, and never run `mvn clean` while `spring-boot:run` is up.
- Schema changes go through a new Flyway migration (database-builder), never by editing an applied one.
- Local run: start Docker Desktop, then in `backend/`: `docker compose up -d` and `mvn spring-boot:run`; in `frontend/`: `npm run dev` (http://localhost:5173). `backend/.env` and `frontend/.env` are local and gitignored.
- Tests: `mvn -q test` in `backend/`. In `frontend/` run `npm test`, `npm run lint`, `npm run build` **from PowerShell**; vitest fails under Git Bash.
- Windows, `core.autocrlf=true`: CRLF warnings are harmless. Keep file encodings as UTF-8.
- Report outcomes honestly: say what was run and what was not (for example, Gmail has never been run against real Google).

## Known gaps and next work (see `docs/handoff.md`)

a live Voyage run (needs a key), using Claude for tailoring, suggestions and questions, Ashby/Workable/SmartRecruiters job sources, Gmail live run (needs Google credentials), a unique index to stop duplicate interview-question lists.
