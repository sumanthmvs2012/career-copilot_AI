package com.careercopilot.interviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.careercopilot.jobs.Company;
import com.careercopilot.jobs.CompanyRepository;
import com.careercopilot.jobs.Job;
import com.careercopilot.jobs.JobRepository;
import com.careercopilot.matches.JobNotFoundException;
import com.careercopilot.matches.MatchService;
import com.careercopilot.matches.SkillExtractor;
import com.careercopilot.profiles.Profile;
import com.careercopilot.profiles.ProfileNotFoundException;
import com.careercopilot.profiles.ProfileRepository;
import com.careercopilot.resumes.Resume;
import com.careercopilot.resumes.ResumeRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InterviewQuestionServiceTest {

    @Mock
    private ProfileRepository profiles;

    @Mock
    private JobRepository jobs;

    @Mock
    private CompanyRepository companies;

    @Mock
    private ResumeRepository resumes;

    @Mock
    private InterviewQuestionRepository questions;

    @Mock
    private MatchService matchService;

    private InterviewQuestionService service;
    private Profile profile;
    private Job job;

    @BeforeEach
    void setUp() {
        service = new InterviewQuestionService(profiles, jobs, companies, resumes, questions,
                new InterviewQuestionGenerator(), matchService, new SkillExtractor(),
                new InterviewQuestionWriter(questions));
        profile = new Profile(1L);
        ReflectionTestUtils.setField(profile, "id", 10L);
        job = new Job("greenhouse", "Backend Engineer", "We use Java and Docker.");
        ReflectionTestUtils.setField(job, "id", 3L);
        job.setCompanyId(7L);
    }

    private void stubAccess() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(matchService.visibleJob(profile, 3L)).thenReturn(job);
        when(companies.findById(7L)).thenReturn(Optional.of(new Company("Stripe")));
    }

    private void stubSaveAll() {
        when(questions.saveAll(any())).thenAnswer(invocation -> {
            List<InterviewQuestion> rows = invocation.getArgument(0);
            long id = 1;
            for (InterviewQuestion row : rows) {
                ReflectionTestUtils.setField(row, "id", id++);
            }
            return rows;
        });
    }

    @Test
    void getReturnsTheStoredListWithoutGeneratingAgain() {
        stubAccess();
        InterviewQuestion stored = new InterviewQuestion(10L, 3L, "Stored question?", "role", 0);
        ReflectionTestUtils.setField(stored, "id", 99L);
        when(questions.findByProfileIdAndJobIdOrderByPositionAscIdAsc(10L, 3L)).thenReturn(List.of(stored));

        InterviewQuestionsResponse response = service.get(1L, 3L);

        assertThat(response.questions()).singleElement().satisfies(q -> {
            assertThat(q.id()).isEqualTo(99L);
            assertThat(q.question()).isEqualTo("Stored question?");
        });
        assertThat(response.company()).isEqualTo("Stripe");
        assertThat(response.jobTitle()).isEqualTo("Backend Engineer");
        verify(questions, never()).saveAll(any());
    }

    @Test
    void getGeneratesAndStoresOnTheFirstCallUsingTheBaseResumeSkills() {
        stubAccess();
        stubSaveAll();
        when(questions.findByProfileIdAndJobIdOrderByPositionAscIdAsc(10L, 3L)).thenReturn(List.of());
        Resume base = new Resume(10L, "k", "cv.pdf", "application/pdf", "Java developer");
        when(resumes.findByProfileIdAndIsBaseTrue(10L)).thenReturn(Optional.of(base));

        InterviewQuestionsResponse response = service.get(1L, 3L);

        assertThat(response.questions()).hasSizeBetween(20, 30);
        assertThat(response.questions()).extracting(InterviewQuestionsResponse.QuestionDto::category)
                .contains("resume_gap");
        // Missing skill lists were extracted and saved, as the match does.
        assertThat(job.getSkills()).isEqualTo("[\"Java\",\"Docker\"]");
        assertThat(base.getSkills()).isEqualTo("[\"Java\"]");
        ArgumentCaptor<List<InterviewQuestion>> saved = ArgumentCaptor.captor();
        verify(questions).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(InterviewQuestion::getPosition)
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, saved.getValue().size()).boxed().toList());
        assertThat(saved.getValue()).allMatch(row -> row.getProfileId().equals(10L) && row.getJobId().equals(3L));
    }

    @Test
    void getWithoutAResumeHasNoResumeGapQuestions() {
        stubAccess();
        stubSaveAll();
        when(questions.findByProfileIdAndJobIdOrderByPositionAscIdAsc(10L, 3L)).thenReturn(List.of());
        when(resumes.findByProfileIdAndIsBaseTrue(10L)).thenReturn(Optional.empty());

        InterviewQuestionsResponse response = service.get(1L, 3L);

        assertThat(response.questions()).hasSizeBetween(20, 30)
                .noneMatch(q -> q.category().equals("resume_gap"));
    }

    @Test
    void regenerateDeletesTheOldListBeforeSavingTheNewOne() {
        stubAccess();
        stubSaveAll();
        when(resumes.findByProfileIdAndIsBaseTrue(10L)).thenReturn(Optional.empty());

        service.regenerate(1L, 3L);

        InOrder order = inOrder(questions);
        order.verify(questions).deleteByProfileIdAndJobId(10L, 3L);
        order.verify(questions).flush();
        order.verify(questions).saveAll(any());
    }

    @Test
    void aProfileIsRequired() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(1L, 3L)).isInstanceOf(ProfileNotFoundException.class);
        assertThatThrownBy(() -> service.regenerate(1L, 3L)).isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    void aJobTheCallerCannotSeeIsNotFoundAndNothingIsStored() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(matchService.visibleJob(profile, 3L)).thenThrow(new JobNotFoundException());

        assertThatThrownBy(() -> service.get(1L, 3L)).isInstanceOf(JobNotFoundException.class);
        assertThatThrownBy(() -> service.regenerate(1L, 3L)).isInstanceOf(JobNotFoundException.class);
        verify(questions, never()).saveAll(any());
        verify(questions, never()).deleteByProfileIdAndJobId(any(), any());
    }

    @Test
    void aLostRaceReturnsTheListTheOtherRequestStored() {
        stubAccess();
        InterviewQuestion stored = new InterviewQuestion(10L, 3L, "Stored question?", "role", 0);
        ReflectionTestUtils.setField(stored, "id", 99L);
        when(questions.findByProfileIdAndJobIdOrderByPositionAscIdAsc(10L, 3L))
                .thenReturn(List.of()).thenReturn(List.of(stored));
        when(resumes.findByProfileIdAndIsBaseTrue(10L)).thenReturn(Optional.empty());
        when(questions.saveAll(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("uq"));

        InterviewQuestionsResponse response = service.get(1L, 3L);

        assertThat(response.questions()).singleElement().satisfies(q -> assertThat(q.id()).isEqualTo(99L));
    }

    @Test
    void aConflictWithNothingStoredIsRethrown() {
        stubAccess();
        when(questions.findByProfileIdAndJobIdOrderByPositionAscIdAsc(10L, 3L)).thenReturn(List.of());
        when(resumes.findByProfileIdAndIsBaseTrue(10L)).thenReturn(Optional.empty());
        when(questions.saveAll(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("uq"));

        assertThatThrownBy(() -> service.get(1L, 3L))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
