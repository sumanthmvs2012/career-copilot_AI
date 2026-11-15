DATABASE

Part one: the tables. Users hold the login details. Profiles link to users and hold job preferences. Resumes link to profiles, with a flag for the base resume;
          tailored copies point back to their base resume and to the job they were tailored for. Companies, jobs, job matches, applications, interview questions,
          email logs and mail connections each have their own table. Application drafts made by the agent are rows in applications with status "draft".

Part two: the columns => Users: id, email, password hash, created at, updated at.
                         Profiles: id, user id, full name, headline, location, remote ok, auto submit enabled (default false), created at, updated at.
                         Resumes: id, profile id, file path (storage key, never a URL), file name, content type, parsed text, is base, base resume id (nullable),
                                  created at, job id (nullable, set on tailored copies).
                                  Skills (jsonb) and embedding (vector 1024) are added by the Phase 5 AI migration.
                         Companies: id, name, website, careers URL, ats type (greenhouse, lever, ashby, workable, smartrecruiters; null for a company typed in
                                    by a user on a pasted job), board token, created at.
                         Jobs: id, company id (nullable for pasted jobs), created by profile id (nullable; set only for source manual, so a pasted job is visible
                               only to the user who pasted it), source (greenhouse, lever, ashby, workable, smartrecruiters, manual), external id (null for manual),
                               title, description, location (raw text from the source), category (nullable; fixed list in ARCHITECTURE.md, indexed),
                               remote type (remote, hybrid, onsite), source URL, embedding (vector 1024, Phase 5), posted at, scraped at, created at.
                         Job locations: id, job id (foreign key to jobs, deleted with the job), city, state, country (each nullable, but at least one is set;
                                        a job can have several rows; indexed by country, state, city and by job id).


Part three: the columns => Job matches: id, profile id, job id, score (0-100), missing skills, near matched skills, missing keywords, suggested headline,
                                         location fit, created at.
                           Applications: id, profile id, job id, resume id, status (draft, applied, interviewing, offer, rejected, withdrawn), cover letter,
                                         submitted at, submitted by (user, auto), created at, updated at.
                           Interview questions: id, job id, application id, question, category, created at.
                           Email logs: id, application id, type (employer reply, reminder), classification (rejection, interview, offer, other; replies only),
                                       subject, occurred at, status.
                           Mail connections: id, user id, provider (gmail), encrypted refresh token, scope, created at, revoked at.


Part four: the rules. Every table gets an id as its primary key, and foreign keys link the tables together.
         Emails are unique in users. Source plus external id is unique in jobs, so the hourly scraper never stores a posting twice.
         Embeddings use the pgvector extension, sized for Voyage voyage-3.5-lite (1024 dimensions).
         Email bodies and resume text are never copied into email logs. Refresh tokens are stored encrypted only.
         Schema changes are made with migrations, and each one is tested before it runs.
