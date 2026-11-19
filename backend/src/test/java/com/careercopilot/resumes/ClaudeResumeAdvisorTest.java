package com.careercopilot.resumes;

import static org.assertj.core.api.Assertions.assertThat;

import com.careercopilot.ai.AiTestSupport;
import com.careercopilot.ai.FakeLlmClient;
import com.careercopilot.jobs.Job;
import com.careercopilot.matches.LocationFit;
import com.careercopilot.matches.MatchAnalysis;
import com.careercopilot.matches.NearMatch;
import com.careercopilot.resumes.ClaudeResumeAdvisor.AiSuggestion;
import com.careercopilot.resumes.ClaudeResumeAdvisor.AiSuggestionList;
import com.careercopilot.resumes.ClaudeResumeAdvisor.AiTailoring;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ClaudeResumeAdvisorTest {

    private final Job job = new Job("greenhouse", "Platform Engineer", "Java, Docker and Kubernetes.");
    private final MatchAnalysis analysis = new MatchAnalysis(50, List.of("Java", "Docker"), List.of("Java"),
            List.of("Docker"), List.of(new NearMatch("Docker", "Podman")), List.of("platform"), "Platform Engineer",
            new LocationFit("unknown", null));

    private Resume resume(String text, String skillsJson) {
        Resume resume = new Resume(10L, "k", "cv.pdf", "application/pdf", text);
        resume.setSkills(skillsJson);
        return resume;
    }

    @Test
    void withoutAKeyBothReturnEmpty() {
        ClaudeResumeAdvisor advisor = new ClaudeResumeAdvisor(AiTestSupport.disabledLlm());
        Resume resume = resume("Java", "[\"Java\"]");

        assertThat(advisor.suggestions(1L, job, resume, analysis, null, false)).isEmpty();
        assertThat(advisor.tailor(1L, job, resume)).isEmpty();
    }

    @Test
    void suggestionsSendTheAnalysisAndUntrustedTextInTags() {
        FakeLlmClient client = FakeLlmClient.replying(new AiSuggestionList(List.of(
                new AiSuggestion("near_match", "Explain how your Podman work maps to Docker."))));
        ClaudeResumeAdvisor advisor = new ClaudeResumeAdvisor(AiTestSupport.llm(client));

        List<SuggestionResponse> result = advisor.suggestions(1L, job, resume("I use Java and Podman.", "[\"Java\"]"),
                analysis, "Austin, TX", true).orElseThrow();

        assertThat(result).containsExactly(
                new SuggestionResponse("near_match", "Explain how your Podman work maps to Docker."));
        assertThat(client.users.get(0))
                .contains("<resume>\nI use Java and Podman.\n</resume>")
                .contains("<job_post>\nJava, Docker and Kubernetes.\n</job_post>")
                .contains("does not show: Docker")
                .contains("Docker (resume has Podman)")
                .contains("Austin, TX, open to remote work");
    }

    @Test
    void cleanSuggestionsFixesTypesAndDropsBadItems() {
        List<AiSuggestion> reply = new ArrayList<>();
        reply.add(new AiSuggestion("ADD_SKILL", "  Add   Docker if you have used it. "));
        reply.add(new AiSuggestion("add_skill", "add docker if you have used it."));
        reply.add(new AiSuggestion("other", "Keep it to one page."));
        reply.add(new AiSuggestion("general", ""));
        reply.add(new AiSuggestion("general", "y".repeat(501)));
        reply.add(null);

        assertThat(ClaudeResumeAdvisor.cleanSuggestions(new AiSuggestionList(reply))).containsExactly(
                new SuggestionResponse("add_skill", "Add Docker if you have used it."),
                new SuggestionResponse("general", "Keep it to one page."));
    }

    @Test
    void cleanSuggestionsKeepsAtMostTwelve() {
        List<AiSuggestion> many = IntStream.range(0, 20).mapToObj(i -> new AiSuggestion("general", "Tip " + i))
                .toList();

        assertThat(ClaudeResumeAdvisor.cleanSuggestions(new AiSuggestionList(many))).hasSize(12);
    }

    @Test
    void tailoringKeepsOnlySkillsTheResumeHas() {
        Resume resume = resume("Built APIs in C++ and Go. Led a team.", "[\"C++\"]");
        FakeLlmClient client = FakeLlmClient.replying(new AiTailoring("Platform Engineer",
                "Engineer who builds APIs in C++ and Go.", List.of("c++", "Go", "Kubernetes", "Golang", "go")));
        ClaudeResumeAdvisor advisor = new ClaudeResumeAdvisor(AiTestSupport.llm(client));

        ClaudeResumeAdvisor.Tailoring result = advisor.tailor(1L, job, resume).orElseThrow();

        // c++ takes the extracted spelling; Go is found in the text; Kubernetes and Golang are not in the resume.
        assertThat(result.relevantSkills()).containsExactly("C++", "Go");
        assertThat(result.headline()).isEqualTo("Platform Engineer");
        assertThat(result.summary()).isEqualTo("Engineer who builds APIs in C++ and Go.");
        assertThat(ClaudeResumeAdvisor.TAILOR_SYSTEM).contains("Use ONLY facts stated in the resume");
    }

    @Test
    void tailoringDropsOverlongFieldsAndAnEmptyReply() {
        Resume resume = resume("Java", "[\"Java\"]");

        ClaudeResumeAdvisor.Tailoring partial = ClaudeResumeAdvisor.cleanTailoring(
                new AiTailoring("h".repeat(201), "Short\nsummary.", List.of()), resume).orElseThrow();
        assertThat(partial.headline()).isNull();
        assertThat(partial.summary()).isEqualTo("Short summary.");

        assertThat(ClaudeResumeAdvisor.cleanTailoring(new AiTailoring(" ", null, List.of("Rust")), resume)).isEmpty();
        assertThat(ClaudeResumeAdvisor.cleanTailoring(null, resume)).isEmpty();
    }
}
