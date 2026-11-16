package com.careercopilot.resumes;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.jobs.Job;
import com.careercopilot.matches.LocationFit;
import com.careercopilot.matches.MatchAnalysis;
import com.careercopilot.matches.NearMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeSuggestionServiceTest {

    private final ResumeSuggestionService service = new ResumeSuggestionService();

    private static MatchAnalysis analysis(List<String> jobSkills, List<String> matched, List<String> missing,
            List<NearMatch> near, List<String> keywords, String locationStatus) {
        return new MatchAnalysis(0, jobSkills, matched, missing, near, keywords, null,
                new LocationFit(locationStatus, "Note."));
    }

    private static List<String> types(List<SuggestionResponse> suggestions) {
        return suggestions.stream().map(SuggestionResponse::type).toList();
    }

    @Test
    void everyMissingSkillGivesAnAddSkillSuggestion() {
        Job job = new Job("greenhouse", "", "d");
        List<SuggestionResponse> out = service.suggest(job, analysis(List.of("Java", "Docker", "Go"),
                List.of("Java"), List.of("Docker", "Go"), List.of(), List.of(), "match"));

        assertThat(out).containsExactly(
                new SuggestionResponse("add_skill", "Add Docker to your resume if you have used it."),
                new SuggestionResponse("add_skill", "Add Go to your resume if you have used it."));
    }

    @Test
    void aNearMatchNamesBothSkills() {
        Job job = new Job("greenhouse", "", "d");
        List<SuggestionResponse> out = service.suggest(job, analysis(List.of("React", "Java"), List.of("Java"),
                List.of("React"), List.of(new NearMatch("React", "React Native")), List.of(), "match"));

        SuggestionResponse near = out.stream().filter(s -> s.type().equals("near_match")).findFirst().orElseThrow();
        assertThat(near.text()).contains("React").contains("React Native");
    }

    @Test
    void keywordSuggestionsAreCappedAtFive() {
        Job job = new Job("greenhouse", "", "d");
        List<String> keywords = List.of("alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta");

        List<SuggestionResponse> out = service.suggest(job,
                analysis(List.of("Java"), List.of("Java"), List.of(), List.of(), keywords, "match"));

        assertThat(types(out)).containsExactly("add_keyword", "add_keyword", "add_keyword", "add_keyword",
                "add_keyword");
        assertThat(out.get(0).text()).contains("alpha");
        assertThat(out.get(4).text()).contains("epsilon");
    }

    @Test
    void aHeadlineSuggestionNeedsARealJobTitle() {
        MatchAnalysis match = analysis(List.of("Java"), List.of("Java"), List.of(), List.of(), List.of(), "match");

        assertThat(service.suggest(new Job("greenhouse", "Platform Engineer", "d"), match))
                .containsExactly(new SuggestionResponse("headline",
                        "Use \"Platform Engineer\" as your headline for this job."));
        assertThat(types(service.suggest(new Job("greenhouse", "", "d"), match))).isEmpty();
        assertThat(types(service.suggest(new Job("manual", "Pasted job", "d"), match))).isEmpty();
    }

    @Test
    void aLocationSuggestionAppearsOnlyForMismatchOrUnknown() {
        Job job = new Job("greenhouse", "", "d");
        for (String status : List.of("mismatch", "unknown")) {
            assertThat(types(service.suggest(job,
                    analysis(List.of("Java"), List.of("Java"), List.of(), List.of(), List.of(), status))))
                    .containsExactly("location");
        }
        for (String status : List.of("match", "partial")) {
            assertThat(service.suggest(job,
                    analysis(List.of("Java"), List.of("Java"), List.of(), List.of(), List.of(), status))).isEmpty();
        }
    }

    @Test
    void aGeneralSuggestionWhenNoJobSkillIsFoundInTheResume() {
        Job job = new Job("greenhouse", "", "d");

        List<SuggestionResponse> out = service.suggest(job,
                analysis(List.of("Docker"), List.of(), List.of("Docker"), List.of(), List.of(), "match"));

        assertThat(types(out)).containsExactly("add_skill", "general");
        assertThat(out.get(1).text()).contains("None of the skills");
    }

    @Test
    void aGeneralSuggestionWhenTheJobHasNoRecognisedSkills() {
        Job job = new Job("greenhouse", "", "d");

        List<SuggestionResponse> out = service.suggest(job,
                analysis(List.of(), List.of(), List.of(), List.of(), List.of(), "match"));

        assertThat(types(out)).containsExactly("general");
        assertThat(out.get(0).text()).contains("could not find specific skills");
    }

    @Test
    void keepsTheDocumentedOrderAndNeverNamesASkillTheJobDidNotAsk() {
        Job job = new Job("greenhouse", "Dev", "d");
        List<SuggestionResponse> out = service.suggest(job, analysis(List.of("React", "Java"), List.of("Java"),
                List.of("React"), List.of(new NearMatch("React", "React Native")), List.of("payments"), "unknown"));

        assertThat(types(out)).containsExactly("add_skill", "near_match", "add_keyword", "headline", "location");
        assertThat(out.stream().map(SuggestionResponse::text).toList().toString()).doesNotContain("Docker");
    }
}
