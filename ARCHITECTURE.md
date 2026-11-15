ARCHITECTURE

Pattern: modular monolith on Spring Boot, because one team and one product don't need microservices yet. 
Frontend: React with Vite. Backend: Spring Boot. 
Database: PostgreSQL with pgvector on Neon. 
AI: Claude through the Anthropic API. 
Hosting: Vercel for the frontend, Render for the backend, Neon for the database.

Sign up: POST, /auth/signup, body {email, password}, creates an account and returns 201 {token}. Returns 409 if the email is taken and 400 for invalid input.

Log in: POST, /auth/login, body {email, password}, returns 200 {token}. Returns 401 with the same message whether the email or the password was wrong.

Get profile: GET, /profile, returns the user's profile as {id, fullName, headline, location, remoteOk, autoSubmitEnabled, createdAt, updatedAt}. Returns 404 if the user has not created a profile yet.

Update profile: PUT, /profile, body {fullName, headline, location, remoteOk, autoSubmitEnabled}, creates the profile on first call and edits it afterwards, returning the same shape as GET. fullName is required (1 to 200 characters); headline and location are optional (up to 200); the booleans default to false. Returns 400 with fieldErrors for invalid input.

Upload resume: POST, /resumes, multipart/form-data with one field "file" (PDF or DOCX, up to 5 MB, checked by extension and file signature). Saves the file through StorageService, extracts its text, and returns 201 {id, fileName, contentType, isBase, baseResumeId, textLength, createdAt}. The user needs a profile first (404 otherwise). A profile stores at most 2 uploaded resumes (decided 2026-11-15): each upload is kept and the newest one becomes the base resume; a third upload is refused with 409 "You already have 2 resumes. Delete one to upload another." Tailored copies do not count toward the 2. Returns 400 for a wrong file type, a file over 5 MB, or a file whose text cannot be read.

Get resume: GET, /resumes/{id}, returns one resume in the same shape plus parsedText. Returns 404 if it does not exist or belongs to another user.

List resumes: GET, /resumes, returns the user's uploaded resumes and tailored copies (base first, then newest first), without parsedText.

Delete resume: DELETE, /resumes/{id}, removes the resume and its file and returns 204. If the base resume is deleted, the newest remaining uploaded resume becomes the base. Returns 404 if it does not exist or belongs to another user.

Tailor resume: POST, /resumes/{id}/tailor, makes a tailored copy for a job without changing the base resume.

Resume suggestions: POST, /resumes/{id}/suggestions?jobId=, returns improvement suggestions for one job (generated on request, not stored).

Match a job: POST, /jobs/{id}/match, returns a score (0-100), missing skills, near-matched skills, missing keywords, a suggested headline and the location fit.

Match a pasted job: POST, /jobs/match, body {title?, companyName?, description, location?, remoteType?, sourceUrl?} (description 20 to 20000 characters; remoteType remote, hybrid or onsite). Saves the pasted description as a job with source "manual" and created_by_profile_id = the caller's profile (needs a profile, 404 otherwise; a typed company name becomes a companies row with no ats type), then returns 201 {job: {id, title, companyName, location, remoteType, source, sourceUrl, description, postedAt, createdAt}, match, message}. Until the AI module ships (Phase 5), match is null and message says matching is not available yet; afterwards match is the result described above.

List jobs: GET, /jobs?scope=pasted|scraped&limit=N, each job has the same fields as the job above (description and postedAt included; postedAt is null for pasted jobs, so the UI shows createdAt as the saved time). scope=pasted returns only the caller's own pasted jobs, scope=scraped only scraped jobs (visible to every signed-in user), no scope returns both. Newest first (postedAt, else createdAt). limit defaults to 50, max 100. Another user's pasted jobs are never returned. Scraped jobs include sourceUrl (the posting link).

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

Auth implementation (2026-11-15):
- JWT: HS256 signed with JWT_SECRET (at least 32 bytes), issuer "career-copilot", subject = user id, lifetime JWT_EXPIRY (default 1 hour). Every route except sign up, log in and /actuator/health needs "Authorization: Bearer <token>".
- Errors: every error returns {status, message}, and validation errors add fieldErrors. Technical details are logged only.
- Rate limiting: 10 requests per minute per IP, counted separately for /auth/login and /auth/signup, kept in memory. This must move to a shared store if the backend ever runs on more than one instance.
- Emails are trimmed and lowercased before saving or lookup. Passwords are hashed with BCrypt.
- Config: env vars DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET, CORS_ALLOWED_ORIGINS, PORT, RESUME_STORAGE_DIR (local-disk resume storage root, default ./storage). Local development reads them from backend/.env (see backend/.env.example).
- Resume files (2026-11-15): StorageService lives in common/storage with a LocalDiskStorageService. Keys are server-generated (resumes/<profileId>/<uuid>.<ext>); the client's file name is stored for display only and never used in a path. Uploads are checked by extension and file signature, limited to 5 MB (also enforced by Spring's multipart limits), and text is extracted with PDFBox (PDF) and Apache POI (DOCX). Blank or unreadable text is rejected with 400.

## Job scraper (decided 2026-11-15)

- A scheduled Spring task (default hourly, `app.scraper.cron`, disabled unless `app.scraper.enabled=true`) fetches each configured board through a `JobSource` implementation (`jobs/scraper/`). First release: Greenhouse (`boards-api.greenhouse.io/v1/boards/{token}/jobs?content=true`) and Lever (`api.lever.co/v0/postings/{token}?mode=json`); Ashby, Workable and SmartRecruiters come later. Public APIs only; no LinkedIn or Indeed.
- Boards are configured as `SCRAPER_BOARDS=greenhouse:stripe,lever:palantir` (source:token). Each board becomes a companies row (ats_type, board_token), created once.
- Jobs are upserted by (source, external_id) so a posting is never stored twice; descriptions are converted from HTML to plain text; at most 200 newest postings per board per run. postedAt comes from the board (Greenhouse first_published, Lever createdAt). Postings that disappear from a board are not deleted in this version.
- Politeness and safety: one request per board per run, connect/read timeouts, a generic User-Agent, one board failing never stops the others, and logs contain only board, counts and HTTP status.
- No manual trigger endpoint (no admin role exists yet).

## Job filters (decided 2026-11-15)

GET /jobs accepts these optional query params in addition to scope and limit; all combine with AND:
- q: text, 1 to 100 characters, case-insensitive "contains" match on title or company name (LIKE wildcards % and _ in user input are escaped).
- source: one of greenhouse, lever, ashby, workable, smartrecruiters, manual.
- remoteType: remote, hybrid or onsite.
- location: text, 1 to 100 characters, case-insensitive "contains" match on the location.
- postedWithin: 1, 7 or 30 (days); compares coalesce(postedAt, createdAt) with now.
- offset: 0 or more (default 0), for "Load more"; the response stays a plain array.
Any invalid value returns 400 {status, message}. Pasted jobs stay owner-only under every filter combination. The frontend has a /jobs page (behind login) with these filters, a Load more button, and the same JobDetails items; the dashboard Latest jobs card links to it ("Browse all jobs").

## Job location and category filters (decided 2026-11-15)

- Jobs get a category (jobs.category, nullable) from this fixed list: Software Engineering, Data & ML, Product, Design, Security, IT & Infrastructure, Sales, Marketing, Customer Support, Operations, Finance, Legal, People & Recruiting, Other. The scraper uses the board's department/team when it maps to one of these, otherwise title keywords, otherwise Other. Pasted jobs are classified from the title the same way.
- A job has zero or more structured locations in a job_locations table (job id, city, state, country; each nullable except at least one set). The scraper parses the board's free-text location (comma, semicolon, slash or "or" separated; "Remote" and "US-Remote" style tokens; US states and Canadian provinces as codes or names; country names and common aliases such as UK, USA, US) into rows. Parsing is best effort: anything it cannot parse leaves the job without structured locations (it still matches the free-text location filter). The original location text is kept unchanged.
- Job JSON adds category (string or null) and locations: [{city, state, country}] (possibly empty).
- GET /jobs adds optional params category (one of the list above), country, state, city: exact case-insensitive match on any of the job's locations (all of them must match on the same location row); combine with every other filter by AND. Invalid category returns 400. Empty values return 400, as for the other filters.
- GET /jobs/facets?scope=scraped|pasted (default scraped) returns {categories: [{value, count}], countries: [...], states: [...], cities: [...]} for the jobs the caller can see in that scope. Optional params country and state narrow states and cities (cascading dropdowns); cities are capped at the 100 most common. Each entry is {value, count}, most common first. Pasted jobs stay owner-only in facets too.
- UI: a shared filter bar (search, category, country, state, city, source, remote type, posted within) on the /jobs page, and a compact version (category, country, state, city) on the dashboard Latest jobs card; dropdown options come from /jobs/facets; choosing a country resets state and city.
