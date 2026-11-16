package com.careercopilot.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.careercopilot.applications.ApplicationAlreadyExistsException;
import com.careercopilot.applications.ApplicationCreateRequest;
import com.careercopilot.applications.ApplicationRepository;
import com.careercopilot.applications.ApplicationService;
import com.careercopilot.jobs.Company;
import com.careercopilot.jobs.CompanyRepository;
import com.careercopilot.jobs.Job;
import com.careercopilot.jobs.JobRepository;
import com.careercopilot.matches.MatchAnalysis;
import com.careercopilot.matches.MatchService;
import com.careercopilot.profiles.Profile;
import com.careercopilot.profiles.ProfileNotFoundException;
import com.careercopilot.profiles.ProfileRepository;
import com.careercopilot.resumes.Resume;
import com.careercopilot.resumes.ResumeRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DraftAgentServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");

    @Mock
    private ProfileRepository profiles;
    @Mock
    private ResumeRepository resumes;
    @Mock
    private JobRepository jobs;
    @Mock
    private CompanyRepository companies;
    @Mock
    private ApplicationRepository applications;
    @Mock
    private ApplicationService applicationService;
    @Mock
    private MatchService matchService;

    private DraftAgentService service;
    private Profile profile;
    private Resume base;

    @BeforeEach
    void setUp() {
        service = new DraftAgentService(profiles, resumes, jobs, companies, applications, applicationService,
                matchService, new AgentProperties(true, null, null), Clock.fixed(NOW, ZoneOffset.UTC));
        profile = new Profile(7L);
        profile.setFullName("Jane Doe");
        ReflectionTestUtils.setField(profile, "id", 3L);
        base = new Resume(3L, "p", "cv.pdf", "application/pdf", "text");
        ReflectionTestUtils.setField(base, "id", 11L);
    }

    private Job job(long id, Instant createdAt) {
        Job job = new Job("greenhouse", "Engineer " + id, "desc");
        ReflectionTestUtils.setField(job, "id", id);
        ReflectionTestUtils.setField(job, "createdAt", createdAt);
        job.setCompanyId(5L);
        return job;
    }

    private static MatchAnalysis analysis(int score) {
        return new MatchAnalysis(score, List.of("Java"), List.of("Java"), List.of(), List.of(), List.of(), null, null);
    }

    private void givenCaller(List<Job> candidates) {
        when(profiles.findByUserId(7L)).thenReturn(Optional.of(profile));
        when(resumes.findByProfileIdAndIsBaseTrue(3L)).thenReturn(Optional.of(base));
        when(jobs.findByCreatedByProfileIdIsNullAndCreatedAtAfterOrderByCreatedAtAscIdAsc(any(), any()))
                .thenReturn(candidates);
    }

    @Test
    void firstRunLooksBackTwentyFourHoursAndMovesTheMarkerToNow() {
        givenCaller(List.of());

        AgentRunResponse result = service.runForUser(7L);

        assertThat(result).isEqualTo(new AgentRunResponse(0, 0));
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(jobs).findByCreatedByProfileIdIsNullAndCreatedAtAfterOrderByCreatedAtAscIdAsc(
                eq(NOW.minusSeconds(24 * 3600)), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(200);
        verify(profiles).updateLastAgentRunAt(3L, NOW);
    }

    @Test
    void laterRunsStartAtTheLastRunMarker() {
        Instant last = NOW.minusSeconds(3600);
        ReflectionTestUtils.setField(profile, "lastAgentRunAt", last);
        givenCaller(List.of());

        service.runForUser(7L);

        verify(jobs).findByCreatedByProfileIdIsNullAndCreatedAtAfterOrderByCreatedAtAscIdAsc(eq(last), any());
    }

    @Test
    void draftsOnlyAtOrAboveTheMinimumScoreWithTheBaseResume() {
        givenCaller(List.of(job(1, NOW), job(2, NOW), job(3, NOW)));
        when(applications.existsByProfileIdAndJobId(anyLong(), anyLong())).thenReturn(false);
        when(matchService.analyze(any(), any(), any())).thenReturn(analysis(69), analysis(70), analysis(95));
        when(companies.findById(5L)).thenReturn(Optional.of(new Company("Acme")));

        AgentRunResponse result = service.runForUser(7L);

        assertThat(result).isEqualTo(new AgentRunResponse(3, 2));
        ArgumentCaptor<ApplicationCreateRequest> sent = ArgumentCaptor.forClass(ApplicationCreateRequest.class);
        verify(applicationService, org.mockito.Mockito.times(2)).create(eq(7L), sent.capture());
        assertThat(sent.getAllValues()).extracting(ApplicationCreateRequest::jobId).containsExactly(2L, 3L);
        assertThat(sent.getAllValues()).allSatisfy(request -> {
            assertThat(request.status()).isEqualTo("draft");
            assertThat(request.resumeId()).isEqualTo(11L);
            assertThat(request.coverLetter()).contains("Acme", "Jane Doe", "Java");
        });
    }

    @Test
    void minimumScoreIsConfigurable() {
        service = new DraftAgentService(profiles, resumes, jobs, companies, applications, applicationService,
                matchService, new AgentProperties(true, 90, null), Clock.fixed(NOW, ZoneOffset.UTC));
        givenCaller(List.of(job(1, NOW), job(2, NOW)));
        when(matchService.analyze(any(), any(), any())).thenReturn(analysis(80), analysis(90));

        assertThat(service.runForUser(7L)).isEqualTo(new AgentRunResponse(2, 1));
    }

    @Test
    void skipsJobsThatAlreadyHaveAnApplicationWithoutMatchingThem() {
        givenCaller(List.of(job(1, NOW)));
        when(applications.existsByProfileIdAndJobId(3L, 1L)).thenReturn(true);

        assertThat(service.runForUser(7L)).isEqualTo(new AgentRunResponse(0, 0));

        verify(matchService, never()).analyze(any(), any(), any());
        verify(applicationService, never()).create(anyLong(), any());
    }

    @Test
    void aLostRaceOnTheUniqueRowIsNotAnError() {
        givenCaller(List.of(job(1, NOW)));
        when(matchService.analyze(any(), any(), any())).thenReturn(analysis(100));
        when(applicationService.create(eq(7L), any())).thenThrow(new ApplicationAlreadyExistsException());

        assertThat(service.runForUser(7L)).isEqualTo(new AgentRunResponse(1, 0));
        verify(profiles).updateLastAgentRunAt(3L, NOW);
    }

    @Test
    void aFullBatchMovesTheMarkerOnlyToTheLastJobSeen() {
        Instant last = NOW.minusSeconds(600);
        List<Job> batch = new ArrayList<>();
        for (int i = 1; i <= DraftAgentService.MAX_JOBS_PER_RUN; i++) {
            batch.add(job(i, i == DraftAgentService.MAX_JOBS_PER_RUN ? last : NOW.minusSeconds(1000)));
        }
        givenCaller(batch);
        when(matchService.analyze(any(), any(), any())).thenReturn(analysis(0));

        service.runForUser(7L);

        verify(profiles).updateLastAgentRunAt(3L, last);
    }

    @Test
    void manualRunNeedsAProfileAndABaseResume() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.runForUser(1L)).isInstanceOf(ProfileNotFoundException.class);

        when(profiles.findByUserId(7L)).thenReturn(Optional.of(profile));
        when(resumes.findByProfileIdAndIsBaseTrue(3L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.runForUser(7L)).isInstanceOf(AgentNoResumeException.class);

        verify(jobs, never()).findByCreatedByProfileIdIsNullAndCreatedAtAfterOrderByCreatedAtAscIdAsc(any(), any());
    }

    @Test
    void scheduledRunCoversEveryProfileAndSurvivesOneFailing() {
        Profile other = new Profile(8L);
        ReflectionTestUtils.setField(other, "id", 4L);
        when(profiles.findAllWithBaseResume()).thenReturn(List.of(other, profile));
        Resume otherBase = new Resume(4L, "p", "cv.pdf", "application/pdf", "text");
        ReflectionTestUtils.setField(otherBase, "id", 12L);
        when(resumes.findByProfileIdAndIsBaseTrue(4L)).thenReturn(Optional.of(otherBase));
        when(resumes.findByProfileIdAndIsBaseTrue(3L)).thenReturn(Optional.of(base));
        when(jobs.findByCreatedByProfileIdIsNullAndCreatedAtAfterOrderByCreatedAtAscIdAsc(any(), any()))
                .thenThrow(new IllegalStateException("db down"))
                .thenReturn(List.of(job(1, NOW)));
        when(matchService.analyze(any(), any(), any())).thenReturn(analysis(100));

        AgentRunResponse result = service.run();

        assertThat(result).isEqualTo(new AgentRunResponse(1, 1));
        verify(applicationService).create(eq(7L), any());
        verify(applicationService, never()).create(eq(8L), any());
    }

    @Test
    void scheduledRunSkipsAProfileWhoseBaseResumeVanished() {
        when(profiles.findAllWithBaseResume()).thenReturn(List.of(profile));
        when(resumes.findByProfileIdAndIsBaseTrue(3L)).thenReturn(Optional.empty());

        assertThat(service.run()).isEqualTo(new AgentRunResponse(0, 0));
        verify(profiles, never()).updateLastAgentRunAt(anyLong(), any());
    }

    @Test
    void neverCallsTheAutoSubmitPath() {
        profile.setAutoSubmitEnabled(true);
        givenCaller(List.of(job(1, NOW)));
        when(matchService.analyze(any(), any(), any())).thenReturn(analysis(100));

        service.runForUser(7L);

        verify(applicationService, never()).submitAuto(anyLong(), anyLong());
        verify(applicationService, never()).submit(anyLong(), anyLong(), any());
    }
}
