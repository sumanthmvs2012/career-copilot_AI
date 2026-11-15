CODE_STYLE

Part one: general rules. Prefer readable code, keep functions focused, reuse existing components and utilities, and avoid unnecessary abstractions.
Naming: React components use PascalCase, like ResumeUpload. Functions and variables use camelCase. Java classes use PascalCase, and Java methods use camelCase. 
        Database tables and columns use snake_case, like job_matches.
        API routes use lowercase with hyphens, like interview-questions.
		
Folder layout For the backend: put each feature in its own module, like auth, profiles, resumes, jobs, and applications. 
                               Each module holds its controller, service, and repository. For the frontend, keep pages, components, 
							   and API calls in separate folders under src.
							   
Frontend folder layout:        put pages in a pages folder, reusable pieces in components, and API calls in services. Shared helpers go in utils.


Error Handling: In Spring Boot, use one global handler that turns errors into a clear response with a status code and a message. 
                In React, show a short message to the user and keep the technical details in the logs.
				
Comments should explain why code exists, not repeat what it does. 
For formatting, use Prettier for the frontend and the standard Spring formatter for the backend