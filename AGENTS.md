AGENTS


Part one: project context. The project is the AI Job Application and Career Copilot, and its users are job seekers.

Part two: the source of truth. Before any significant change, read the files in the docs folder. 
          Those are the PRD, design system, architecture, database, security, code style, and testing files. 
		  Don't contradict them without first pointing out the conflict.
		  
Part three: general rules. Understand existing code before changing it. Reuse existing components and utilities. 
           Don't invent business requirements, don't expose secrets, and don't change unrelated files.

Part four: before any non-trivial task, read the relevant docs and code, identify affected files and dependencies, 
           and write a short plan before coding. After coding, run the tests, lint, and build, then review the changed files.

Part five: completion. A task is done only when the requirements are met, the tests pass, the build succeeds, no unintended files 
                       changed, the security rules are respected, and the docs are updated. If requirements conflict, ask instead of guessing.