package com.careercopilot.resumes;

import com.careercopilot.jobs.Job;
import com.careercopilot.matches.MatchAnalysis;
import com.careercopilot.matches.MatchService;
import com.careercopilot.matches.NearMatch;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** The rule-based suggestion rules. They only advise; nothing is added to a resume. */
@Service
public class ResumeSuggestionService {

    static final int MAX_KEYWORDS = 5;

    public List<SuggestionResponse> suggest(Job job, MatchAnalysis match) {
        List<SuggestionResponse> out = new ArrayList<>();
        for (String skill : match.missingSkills()) {
            out.add(new SuggestionResponse("add_skill", "Add " + skill + " to your resume if you have used it."));
        }
        for (NearMatch near : match.nearMatchedSkills()) {
            out.add(new SuggestionResponse("near_match", "The job asks for " + near.jobSkill()
                    + " and your resume has " + near.resumeSkill() + ". If you have used " + near.jobSkill()
                    + ", say so; otherwise explain how your " + near.resumeSkill() + " work relates."));
        }
        match.missingKeywords().stream().limit(MAX_KEYWORDS).forEach(keyword -> out.add(new SuggestionResponse(
                "add_keyword", "The job description often uses \"" + keyword
                        + "\" and your resume does not. Use it if it describes your work.")));
        String title = job.getTitle() == null ? "" : job.getTitle().strip();
        if (!title.isEmpty() && !MatchService.DEFAULT_TITLE.equals(title)) {
            out.add(new SuggestionResponse("headline", "Use \"" + title + "\" as your headline for this job."));
        }
        String status = match.locationFit() == null ? "unknown" : match.locationFit().status();
        if ("mismatch".equals(status) || "unknown".equals(status)) {
            String note = match.locationFit() == null ? null : match.locationFit().note();
            out.add(new SuggestionResponse("location", note == null || note.isBlank()
                    ? "Check that the job location works for you and state your location or remote preference."
                    : note + " State your location or remote preference on your resume."));
        }
        if (match.matchedSkills().isEmpty()) {
            out.add(new SuggestionResponse("general", match.jobSkills().isEmpty()
                    ? "We could not find specific skills in this job post. Read it and mirror its wording where it is true for you."
                    : "None of the skills this job asks for were found in your resume. Add the ones you have, or consider whether this job fits."));
        }
        return out;
    }
}
