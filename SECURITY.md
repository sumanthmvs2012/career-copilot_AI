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
