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

Match a job: POST, /jobs/{id}/match, matches the caller's base resume against a job the caller can see (scraped, or their own pasted job; 404 otherwise) and returns 200 with the match result below, saving it. Needs a profile (404) and a base resume (409 "Upload a resume first to see your match."). Running it again replaces the stored result for that job. GET, /jobs/{id}/match, returns the stored result (404 if none yet).

Match result (decided 2026-11-15, keyword matching with no AI keys; Claude and embeddings replace the internals later without changing this shape): {jobId, resumeId, score, matchedSkills, missingSkills, nearMatchedSkills: [{jobSkill, resumeSkill}], missingKeywords, suggestedHeadline, locationFit: {status, note}, createdAt}.
- Skills come from a curated dictionary (classpath resource, each skill with aliases and optional related skills), found case-insensitively on word boundaries (so "C++", "C#", ".NET" and "Node.js" work) in the resume text and in the job description. They are stored as a JSON array in TEXT columns (resumes.skills, jobs.skills) so the same migration runs on H2 and PostgreSQL; the Phase 5 migration may convert them to jsonb. Resume skills are extracted at upload; jobs and older resumes are extracted on first match.
- score = round(100 * matched / job skills), where a job skill counts as matched when the resume has it or an alias of it; a job with no recognised skills scores 0 with an explanatory note. missingSkills are job skills the resume lacks; nearMatchedSkills pair a missing job skill with a related resume skill (e.g. "React" vs "React Native"); missingKeywords are up to 10 frequent job-description terms (not stop words, not already a skill) that the resume text lacks.
- suggestedHeadline is the job title when the job has one, else the caller's profile headline, else null. locationFit.status is "match", "partial", "mismatch" or "unknown": remote jobs match when the profile is open to remote work; otherwise the job location is compared with the profile location (same city or state or country = match/partial); anything missing is "unknown". note is a short sentence.
- Stored in job_matches (profile id, job id, resume id, score, missing skills, near matched skills, missing keywords, suggested headline, location fit, created at; one row per profile and job). Resume responses (POST/GET /resumes, GET /resumes/{id}) gain skills: [string].

Match a pasted job: POST, /jobs/match, body {title?, companyName?, description, location?, remoteType?, sourceUrl?} (description 20 to 20000 characters; remoteType remote, hybrid or onsite). Saves the pasted description as a job with source "manual" and created_by_profile_id = the caller's profile (needs a profile, 404 otherwise; a typed company name becomes a companies row with no ats type), then returns 201 {job: {id, title, companyName, location, remoteType, source, sourceUrl, description, postedAt, createdAt}, match, message}. When the caller has a base resume, match is the result described above (computed and stored, same as POST /jobs/{id}/match) and message says so; when there is no base resume, match is null and message says "Upload a resume to see your match.".

List jobs: GET, /jobs?scope=pasted|scraped&limit=N, each job has the same fields as the job above (description and postedAt included; postedAt is null for pasted jobs, so the UI shows createdAt as the saved time). scope=pasted returns only the caller's own pasted jobs, scope=scraped only scraped jobs (visible to every signed-in user), no scope returns both. Newest first (postedAt, else createdAt). limit defaults to 50, max 100. Another user's pasted jobs are never returned. Scraped jobs include sourceUrl (the posting link).

Create application: POST, /applications, body {jobId, resumeId?, status?, coverLetter?}. jobId is required and must be a job the caller can see (a scraped job or their own pasted job), else 404. resumeId is optional and must be one of the caller's resumes, else 404. status is "draft" or "applied" (default "draft"); coverLetter is up to 10000 characters. Needs a profile (404 otherwise). One application per job per profile: a second one returns 409 "You already track this job." Setting status "applied" sets submittedAt to now; submittedBy is "user". Returns 201 {id, jobId, jobTitle, company, resumeId, status, coverLetter, appliedAt, submittedBy, emailSent, emailSentAt, createdAt, updatedAt}. appliedAt is submittedAt (null for drafts). emailSent is false and emailSentAt null until email tracking ships (decided 2026-11-15, with the applications table).

List applications: GET, /applications, returns the caller's applications (same shape as above), newest first, including agent drafts (status "draft"). Never another user's.

Update application: PUT, /applications/{id}, body {status?, coverLetter?} with at least one field. status is one of draft, applied, interviewing, offer, rejected, withdrawn. Moving to "applied" for the first time sets submittedAt. Returns 200 with the same shape. 404 if it does not exist or belongs to another user; 400 for an invalid status or an empty body.

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
- A job has zero or more structured locations in a job_locations table (job id, city, state, country; each nullable except at least one set). The scraper parses the board's free-text location (comma, semicolon, slash or "or" separated; "Remote" and "US-Remote" style tokens; US states and Canadian provinces as codes or names; country names and common aliases such as UK, USA, US) into rows. Parsing is best effort: anything it cannot parse leaves the job without structured locations (it still matches the free-text location filter). The original location text is kept unchanged. Normalization: known city aliases are collapsed to one name (SF to San Francisco, NYC and New York City to New York, SEA to Seattle, Bangalore and IN-Bengaluru to Bengaluru; a known city before a dash such as NYC-Privy is just the city), junk tokens such as "Location" are dropped, a country name such as Taiwan is a country and never a city, and a city is never stored as a state (states come only from the supported US states and Canadian provinces, or from an unknown region between a city and a non-US/Canada country, and never from a known city name). Re-deriving existing rows: the scraper rewrites the locations of every posting it re-fetches, and on every startup JobLocationReparser re-parses the stored free-text location of ALL jobs (scraped and pasted) in batches of 500 and replaces job_locations only for jobs whose parsed rows differ from the stored ones. It is idempotent (a second run changes nothing), never deletes or edits a job, and can be switched off with app.locations.reparse-on-startup=false (env LOCATIONS_REPARSE_ON_STARTUP).
- Job JSON adds category (string or null) and locations: [{city, state, country}] (possibly empty).
- GET /jobs adds optional params category (one of the list above), country, state, city: exact case-insensitive match on any of the job's locations (all of them must match on the same location row); combine with every other filter by AND. Invalid category returns 400. Empty values return 400, as for the other filters.
- GET /jobs/facets returns {categories: [{value, count}], countries: [...], states: [...], cities: [...]} (most common first, count = distinct jobs; cities capped at the 100 most common). It accepts every filter GET /jobs accepts (scope scraped|pasted with default scraped, q, category, source, remoteType, location, postedWithin, country, state, city) with the same validation (400 on invalid or empty values). Faceted search: each list is computed with all active filters applied except its own dimension, so choosing a category narrows the countries, states and cities (and counts) to places that have jobs in it, choosing a country narrows categories, states and cities, and choosing a state narrows cities to that state; place filters are matched on the same location row as the listed value. Pasted jobs stay owner-only in facets too. It runs four queries (one per list).
- UI: a shared filter bar (search, category, country, state, city, source, remote type, posted within) on the /jobs page, and a compact version (category, country, state, city) on the dashboard Latest jobs card; dropdown options come from /jobs/facets; choosing a country resets state and city.
