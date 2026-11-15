Testing

 Part one: tools. Use JUnit and Mockito for the backend, MockMvc for the API tests, and Vitest with React Testing Library for the frontend
 Part two: the critical flows, each with its expected result. 
         Sign up: the account is created and the user gets a token. 
		 Log in: a valid password returns a token, and a wrong one is refused. 
		 Upload resume: the file is saved and its text is extracted. 
         Match a job: a score and missing skills come back. 
		 Create application: it appears on the dashboard, with status set to applied.
 
 
 Part three: coverage and commands. Aim for around eighty percent coverage on the backend services. 
             Backend tests run with mvn test (from backend/; they use an in-memory H2 database in PostgreSQL mode, and will move to Testcontainers with pgvector once vector columns arrive), and the frontend with npm test. 
             The build uses mvn package, and linting uses npm run lint. These commands start working once the code exists.
             The frontend build runs with npm run build (run frontend commands from the frontend/ folder).