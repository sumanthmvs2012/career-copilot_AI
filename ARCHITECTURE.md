ARCHITECTURE

Pattern: modular monolith on Spring Boot, because one team and one product don't need microservices yet. 
Frontend: React with Vite. Backend: Spring Boot. 
Database: PostgreSQL with pgvector on Neon. 
AI: Claude through the Anthropic API. 
Hosting: Vercel for the frontend, Render for the backend, Neon for the database.

Sign up: POST, /auth/signup, creates an account.

Log in: POST, /auth/login, returns a JWT token.

Get profile: GET, /profile, returns the user's profile.

Update profile: PUT, /profile, edits the profile.

Upload resume: POST, /resumes, stores a PDF or DOCX.

Get resume: GET, /resumes/{id}, returns one resume.

List resumes: GET, /resumes, returns the user's base resume and tailored copies.

Tailor resume: POST, /resumes/{id}/tailor, makes a tailored copy for a job without changing the base resume.

Resume suggestions: POST, /resumes/{id}/suggestions?jobId=, returns improvement suggestions for one job (generated on request, not stored).

Match a job: POST, /jobs/{id}/match, returns a score (0-100), missing skills, near-matched skills, missing keywords, a suggested headline and the location fit.

Match a pasted job: POST, /jobs/match, saves the pasted description as a job with source "manual", then returns the same result as above.

List jobs: GET, /jobs, returns scraped jobs.

Create application: POST, /applications, saves an application.

List applications: GET, /applications, returns the user's applications, including agent drafts (status "draft").

Update application: PUT, /applications/{id}, changes the status or the cover letter.

Submit application: POST, /applications/{id}/submit, submits a draft once the user approves it. If auto-submit is on, the agent calls the same service itself.

Interview questions: GET, /jobs/{id}/interview-questions, returns questions for a job.

Connect mailbox: POST, /mail-connections, starts Gmail read-only OAuth. DELETE, /mail-connections/{id}, revokes it.


There is the AI layer, the file uploads, the hourly scraper, and logging. Claude handles resume improvements, interview questions, and application drafts. 
Resumes are PDF or DOCX, up to five megabytes, stored in cloud storage. A scheduled Spring Boot task scrapes jobs every hour. 
Logging uses SLF4J with Logback, and errors go to Sentry.


Decisions (2026-11-15, see docs/plan.md section 1.3):
- Job sources: each board is one class behind a JobSource interface. Greenhouse, Lever and Ashby come first, then Workable and SmartRecruiters. Only sources that allow automated access are used.
- File storage: a StorageService interface. Local disk in development, Cloudflare R2 (S3-compatible) before launch, because Render's disk is wiped on redeploy.
- Embeddings: Voyage AI (voyage-3.5-lite), with the vectors stored in pgvector.
- Email tracking: read-only mailbox access with the user's OAuth consent, to find employer replies. A scheduled task sends follow-up reminders. Gmail comes first. Reminders fire 7 days after applying with no reply, shown in the app (the timing can be changed later).
- CI: GitHub Actions runs the tests, lint and build.
