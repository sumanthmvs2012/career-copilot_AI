
PRD

 AI Job Application and Career Copilot. It's a web app where a job seeker creates a profile and uploads a resume once, then tailors a copy of it for each job.
 It matches the profile against job descriptions, shows the missing skills, and tracks every application on a dashboard.

Story:As a job seeker, I can create a profile and upload my resume once, and edit it at any time. 
As a job seeker, I can tailor a copy of my resume for each job without changing my base profile. As a job seeker,
I can paste a job description and see my match score and missing skills.

As a job seeker, I can see a dashboard with each application's status, date, whether an email was sent, and when.
As a job seeker, I get resume improvement suggestions for a specific job. As a job seeker, 
I get twenty or more interview questions after I apply, based on the company and my resume, which I can copy with one click.

Last part: as a job seeker, an agent collects new job postings from sites like Greenhouse every hour and prepares an application draft for each one. 
The agent asks before submitting any application, unless I have turned on auto-submit in my settings. The dashboard is built early and grows as each feature ships.

/
Part one: the product overview. AI Job Application and Career Copilot is a web app where a job seeker creates a profile, 
          uploads a resume once, and tailors a copy for each job. 
          It matches the profile against job descriptions, shows missing skills, and tracks every application on a dashboard
		  
Part two: the stories. As a job seeker, I can create a profile, upload my resume once, and edit it any time. 
          a job seeker, I can tailor a copy of my resume for each job without changing my base profile. 
		  As a job seeker, I can paste a job description and see my match score and missing skills. 
		  As a job seeker, I get resume improvement suggestions for a specific job. As a job seeker, 
		  I get at least twenty interview questions after I apply, which I can copy with one click. 
          As a job seeker, an agent collects new job postings from sites like Greenhouse every hour and prepares an application draft for each one.


Decisions (2026-11-15, see docs/plan.md section 1.3):
- Submitting: the agent asks before submitting an application. If the user turns on auto-submit, it submits without asking, but only where the job board allows applications through an API.
- Drafts: written only for postings with a match score of 70 or more.
- Match result: a score from 0 to 100, missing skills, almost-matched skills, missing keywords, the most suitable headline or job title, and how well the location fits.
- Email tracking: the dashboard shows replies from employers (rejection, interview/acceptance, other) and follow-up reminders after applying.
- Job sources: public job board APIs and company careers boards (Greenhouse, Lever and Ashby first), only where automated access is allowed.
