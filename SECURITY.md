SECURITY

Part one: authentication uses email and password, with JWT tokens. Every endpoint checks that the user owns the data it touches. 
Secrets live in environment variables, never in the code. Resume uploads are limited to PDF and DOCX, up to five megabytes. 
Rate limiting applies to login and sign up.

Part two: logging, Errors are logged with SLF4J and Logback, and sent to Sentry. Never log passwords, tokens, or API keys. Logs also never include resume text. 
Sign-in events and failed requests get logged, so problems can be traced, but details stay out of the response sent to the user.

Decisions (2026-11-15):
- Mailbox access uses OAuth with the smallest read-only scope. The refresh tokens are encrypted in the database, never logged, and the user can revoke them at any time. Email bodies are never logged.
- Auto-submit is off by default and only the user can turn it on. Every submission, automatic or not, is logged against the application.
- Resume files on local disk are for development only. Production uses private R2 storage with links that expire.

AI providers (2026-11-19):
- Resume and job text is sent to Voyage AI (embeddings) only when VOYAGE_API_KEY is set, and to Anthropic only when ANTHROPIC_API_KEY is set. Without keys nothing leaves the system.
- API keys come from environment variables only, are never logged or returned, and provider errors reach the client as fixed messages.
- Texts, prompts, vectors and provider responses are never logged; only exception class names and HTTP status codes are.
- Each user is capped at AI_DAILY_LIMIT_PER_USER provider calls per day (embeddings and Claude together).
- Job posts and resume text are untrusted: prompts wrap them in data tags and tell Claude to ignore instructions inside them. Claude's output is checked before use. Tailoring keeps only skills found in the resume and never edits the original text.
