package com.careercopilot.interviews;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Rule-based interview questions from templates, with no AI. The same input always gives the same list (no
 * randomness, no clock), a question never appears twice, and the list always has 20 to 30 entries.
 */
@Component
public class InterviewQuestionGenerator {

    public static final String TECHNICAL = "technical";
    public static final String BEHAVIORAL = "behavioral";
    public static final String ROLE = "role";
    public static final String COMPANY = "company";
    public static final String RESUME_GAP = "resume_gap";

    static final int MIN_QUESTIONS = 20;
    static final int MAX_QUESTIONS = 30;

    private static final int MAX_SKILL_QUESTIONS = 8;
    private static final int MAX_GAP_QUESTIONS = 4;
    private static final int MAX_STRENGTH_QUESTIONS = 2;

    /** Everything the rules look at. resumeSkills is null when the caller has no base resume. */
    public record Input(String jobTitle, String company, List<String> jobSkills, List<String> resumeSkills) {
    }

    public record Generated(String question, String category) {
    }

    public List<Generated> generate(Input input) {
        String role = blankToNull(input.jobTitle()) == null ? "this role" : input.jobTitle().strip();
        String company = blankToNull(input.company());
        String companyName = company == null ? "this company" : company;
        List<String> jobSkills = input.jobSkills() == null ? List.of() : input.jobSkills();

        Map<String, List<String>> byCategory = new LinkedHashMap<>();
        List<String> technical = new ArrayList<>(skillQuestions(jobSkills));
        List<String> role1 = roleQuestions(role);
        List<String> company1 = companyQuestions(companyName);
        List<String> behavioral = behavioralQuestions();
        List<String> gaps = input.resumeSkills() == null ? List.of() : gapQuestions(jobSkills, input.resumeSkills());

        // Core first; the extras below only top the list up when a job has few skills or little text.
        byCategory.put(TECHNICAL, technical);
        byCategory.put(RESUME_GAP, new ArrayList<>(gaps));
        byCategory.put(ROLE, new ArrayList<>(role1.subList(0, 4)));
        byCategory.put(COMPANY, new ArrayList<>(company1));
        byCategory.put(BEHAVIORAL, new ArrayList<>(behavioral.subList(0, 7)));

        List<Generated> result = flatten(byCategory);
        if (result.size() < MIN_QUESTIONS) {
            List<Generated> extras = new ArrayList<>();
            genericTechnical(role).forEach(q -> extras.add(new Generated(q, TECHNICAL)));
            role1.subList(4, role1.size()).forEach(q -> extras.add(new Generated(q, ROLE)));
            behavioral.subList(7, behavioral.size()).forEach(q -> extras.add(new Generated(q, BEHAVIORAL)));
            Set<String> seen = new LinkedHashSet<>();
            result.forEach(g -> seen.add(key(g.question())));
            for (Generated extra : extras) {
                if (result.size() >= MIN_QUESTIONS) {
                    break;
                }
                if (seen.add(key(extra.question()))) {
                    byCategory.get(extra.category()).add(extra.question());
                    result = flatten(byCategory);
                }
            }
        }
        return result.size() > MAX_QUESTIONS ? List.copyOf(result.subList(0, MAX_QUESTIONS)) : List.copyOf(result);
    }

    /** Category order is fixed, and a repeated question (compared case-insensitively) is dropped. */
    private static List<Generated> flatten(Map<String, List<String>> byCategory) {
        List<Generated> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : byCategory.entrySet()) {
            for (String question : entry.getValue()) {
                if (seen.add(key(question))) {
                    out.add(new Generated(question, entry.getKey()));
                }
            }
        }
        return out;
    }

    private static List<String> skillQuestions(List<String> jobSkills) {
        List<String> templates = List.of(
                "Describe a project where you used %s. What did you build and what would you do differently now?",
                "What are the common mistakes people make with %s, and how do you avoid them?",
                "How do you decide whether %s is the right tool for a problem?",
                "How would you test and debug a feature that relies on %s?");
        List<String> questions = new ArrayList<>();
        int count = Math.min(jobSkills.size(), MAX_SKILL_QUESTIONS);
        for (int i = 0; i < count; i++) {
            questions.add(templates.get(i % templates.size()).formatted(jobSkills.get(i)));
        }
        // With few skills, ask a second, different question about the first ones so the list is not thin.
        for (int i = 0; i < count && count < 4; i++) {
            questions.add(templates.get((i + 1) % templates.size()).formatted(jobSkills.get(i)));
        }
        return questions;
    }

    private static List<String> gapQuestions(List<String> jobSkills, List<String> resumeSkills) {
        Set<String> have = new LinkedHashSet<>(resumeSkills);
        List<String> questions = new ArrayList<>();
        int missing = 0;
        int strengths = 0;
        for (String skill : jobSkills) {
            if (have.contains(skill)) {
                if (strengths < MAX_STRENGTH_QUESTIONS) {
                    questions.add("Your resume lists %s. Walk me through the most demanding work you did with it."
                            .formatted(skill));
                    strengths++;
                }
            } else if (missing < MAX_GAP_QUESTIONS) {
                questions.add(("This role uses %s, which I do not see on your resume. How would you get "
                        + "productive with it in your first month?").formatted(skill));
                missing++;
            }
        }
        return questions;
    }

    private static List<String> genericTechnical(String role) {
        return List.of(
                "Walk me through how you would approach a hard technical problem as a %s.".formatted(role),
                "How do you keep code or work quality high when deadlines are tight?",
                "Tell me about a bug or failure that was hard to find and how you solved it.",
                "How do you review someone else's work and give useful feedback?",
                "How do you decide what to test, and what is good enough to ship?",
                "How do you stay current with the tools and practices in your field?");
    }

    private static List<String> roleQuestions(String role) {
        return List.of(
                "Why are you interested in the %s role?".formatted(role),
                "What would you want to achieve in your first 90 days as %s?".formatted(role),
                "Which parts of the %s role do you expect to be hardest, and how will you handle them?"
                        .formatted(role),
                "How does your past experience prepare you for the %s role?".formatted(role),
                "Where do you want this role to take you in the next three years?");
    }

    private static List<String> companyQuestions(String company) {
        return List.of(
                "Why do you want to work at %s?".formatted(company),
                "What do you know about what %s builds and who its customers are?".formatted(company),
                "What do you think is the biggest challenge %s faces right now?".formatted(company),
                "What questions do you have for the team at %s?".formatted(company));
    }

    private static List<String> behavioralQuestions() {
        return List.of(
                "Tell me about a time you disagreed with a teammate. How did you resolve it?",
                "Describe a time you missed a deadline or made a mistake. What did you learn?",
                "Tell me about a time you had to learn something new very quickly.",
                "Describe a time you took ownership of something nobody asked you to do.",
                "Tell me about a time you had to explain a complex idea to someone non-technical.",
                "How do you handle several competing priorities at once?",
                "Tell me about the achievement you are most proud of and why.",
                "Describe a time you received difficult feedback. What did you do with it?",
                "Tell me about a time you worked with an unclear goal. How did you move forward?",
                "How do you work with people whose style is very different from yours?");
    }

    private static String key(String question) {
        return question.toLowerCase(Locale.ROOT).strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
