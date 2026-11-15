package com.careercopilot.matches;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.careercopilot.jobs.Job;
import com.careercopilot.jobs.JobLocation;
import com.careercopilot.jobs.JobLocationRepository;
import com.careercopilot.jobs.JobRepository;
import com.careercopilot.profiles.Profile;
import com.careercopilot.profiles.ProfileNotFoundException;
import com.careercopilot.profiles.ProfileRepository;
import com.careercopilot.resumes.Resume;
import com.careercopilot.resumes.ResumeRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock
    private ProfileRepository profiles;

    @Mock
    private JobRepository jobs;

    @Mock
    private ResumeRepository resumes;

    @Mock
    private JobMatchRepository matches;

    @Mock
    private JobLocationRepository locations;

    private MatchService service;
    private Profile profile;

    @BeforeEach
    void setUp() {
        service = new MatchService(profiles, jobs, resumes, matches, locations, new SkillExtractor());
        profile = new Profile(1L);
        ReflectionTestUtils.setField(profile, "id", 10L);
    }

    private Job job(long id, String title, String description, Long ownerProfileId) {
        Job job = new Job(ownerProfileId == null ? "greenhouse" : "manual", title, description);
        ReflectionTestUtils.setField(job, "id", id);
        job.setCreatedByProfileId(ownerProfileId);
        return job;
    }

    private Resume resume(String text, String skillsJson) {
        Resume resume = new Resume(10L, "k", "cv.pdf", "application/pdf", text);
        ReflectionTestUtils.setField(resume, "id", 3L);
        resume.setSkills(skillsJson);
        return resume;
    }

    /** Stubs everything a successful match needs and echoes the saved row back. */
    private void stubHappyPath(Job job, Resume resume) {
        lenient().when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        lenient().when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        lenient().when(resumes.findByProfileIdAndIsBaseTrue(10L)).thenReturn(Optional.of(resume));
        lenient().when(locations.findByJobIdIn(any(Collection.class))).thenReturn(List.of());
        lenient().when(matches.findByProfileIdAndJobId(10L, job.getId())).thenReturn(Optional.empty());
        lenient().when(matches.save(any(JobMatch.class))).thenAnswer(inv -> {
            JobMatch saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "createdAt", java.time.Instant.parse("2026-11-15T10:00:00Z"));
            return saved;
        });
    }

    @Test
    void scoreIsTheRoundedPercentageOfJobSkillsTheResumeHas() {
        Job job = job(7L, "Backend Engineer", "Needs Java, Docker and Kubernetes", null);
        stubHappyPath(job, resume("Java and Docker work", "[\"Java\",\"Docker\"]"));

        MatchResponse result = service.matchJob(1L, 7L);

        assertThat(result.score()).isEqualTo(67);
        assertThat(result.matchedSkills()).containsExactly("Java", "Docker");
        assertThat(result.missingSkills()).containsExactly("Kubernetes");
        assertThat(result.jobId()).isEqualTo(7L);
        assertThat(result.resumeId()).isEqualTo(3L);
        assertThat(result.createdAt()).isNotNull();
    }

    @Test
    void aFullMatchScoresOneHundredAndNothingIsMissing() {
        Job job = job(7L, "Dev", "Python and Redis", null);
        stubHappyPath(job, resume("text", "[\"Python\",\"Redis\",\"Git\"]"));

        MatchResponse result = service.matchJob(1L, 7L);

        assertThat(result.score()).isEqualTo(100);
        assertThat(result.missingSkills()).isEmpty();
    }

    @Test
    void aJobWithNoRecognisedSkillsScoresZero() {
        Job job = job(7L, "Chef", "Cook meals for guests", null);
        stubHappyPath(job, resume("text", "[\"Java\"]"));

        MatchResponse result = service.matchJob(1L, 7L);

        assertThat(result.score()).isZero();
        assertThat(result.matchedSkills()).isEmpty();
        assertThat(result.missingSkills()).isEmpty();
    }

    @Test
    void aRelatedResumeSkillIsANearMatchForAMissingJobSkill() {
        Job job = job(7L, "Frontend", "We build with React and TypeScript", null);
        stubHappyPath(job, resume("text", "[\"React Native\",\"TypeScript\"]"));

        MatchResponse result = service.matchJob(1L, 7L);

        assertThat(result.matchedSkills()).containsExactly("TypeScript");
        assertThat(result.missingSkills()).containsExactly("React");
        assertThat(result.nearMatchedSkills()).containsExactly(new NearMatch("React", "React Native"));
        assertThat(result.score()).isEqualTo(50);
    }

    @Test
    void aMissingSkillWithNoRelatedResumeSkillHasNoNearMatch() {
        Job job = job(7L, "Dev", "Needs Terraform", null);
        stubHappyPath(job, resume("text", "[\"Python\"]"));

        assertThat(service.matchJob(1L, 7L).nearMatchedSkills()).isEmpty();
    }

    @Test
    void missingKeywordsAreFrequentJobTermsTheResumeLacks() {
        String description = "Payments platform. Own payments reliability and observability. "
                + "Observability matters; payments are our core. Java services.";
        Job job = job(7L, "Engineer", description, null);
        stubHappyPath(job, resume("I wrote Java services and observability dashboards", "[\"Java\"]"));

        MatchResponse result = service.matchJob(1L, 7L);

        // "observability" is on the resume, "java" and "services" are a skill and plural of a resume word.
        assertThat(result.missingKeywords()).containsExactly("payments");
    }

    @Test
    void missingKeywordsAreCappedAtTen() {
        StringBuilder text = new StringBuilder("Java ");
        for (char c = 'a'; c <= 'o'; c++) {
            text.append("zeta").append(c).append(" zeta").append(c).append(' ');
        }
        Job job = job(7L, "Dev", text.toString(), null);
        stubHappyPath(job, resume("nothing relevant", "[]"));

        assertThat(service.matchJob(1L, 7L).missingKeywords()).hasSize(10);
    }

    @Test
    void headlineIsTheJobTitle() {
        Job job = job(7L, "  Staff Engineer ", "Java", null);
        profile.setHeadline("My headline");
        stubHappyPath(job, resume("t", "[]"));

        assertThat(service.matchJob(1L, 7L).suggestedHeadline()).isEqualTo("Staff Engineer");
    }

    @Test
    void headlineFallsBackToTheProfileHeadlineForADefaultTitle() {
        Job job = job(7L, "Pasted job", "Java", 10L);
        profile.setHeadline(" Backend developer ");
        stubHappyPath(job, resume("t", "[]"));

        assertThat(service.matchJob(1L, 7L).suggestedHeadline()).isEqualTo("Backend developer");
    }

    @Test
    void headlineIsNullWhenThereIsNeitherTitleNorProfileHeadline() {
        Job job = job(7L, "Pasted job", "Java", 10L);
        stubHappyPath(job, resume("t", "[]"));

        assertThat(service.matchJob(1L, 7L).suggestedHeadline()).isNull();
    }

    @Test
    void locationFitUsesTheProfileAndJobLocations() {
        Job job = job(7L, "Dev", "Java", null);
        job.setLocation("Berlin, Germany");
        profile.setLocation("Berlin, Germany");
        stubHappyPath(job, resume("t", "[]"));
        when(locations.findByJobIdIn(any(Collection.class)))
                .thenReturn(List.of(new JobLocation(7L, "Berlin", null, "Germany")));

        MatchResponse result = service.matchJob(1L, 7L);

        assertThat(result.locationFit().status()).isEqualTo("match");
        assertThat(result.locationFit().note()).isNotBlank();
    }

    @Test
    void theSavedRowHoldsJsonListsAndReplacesTheOldOne() {
        Job job = job(7L, "Dev", "Java and Docker", null);
        stubHappyPath(job, resume("t", "[\"Java\"]"));
        JobMatch old = new JobMatch(10L, 7L);
        when(matches.findByProfileIdAndJobId(10L, 7L)).thenReturn(Optional.of(old));

        service.matchJob(1L, 7L);

        verify(matches).delete(old);
        verify(matches).flush();
        ArgumentCaptor<JobMatch> captor = ArgumentCaptor.forClass(JobMatch.class);
        verify(matches).save(captor.capture());
        JobMatch row = captor.getValue();
        assertThat(row.getProfileId()).isEqualTo(10L);
        assertThat(row.getJobId()).isEqualTo(7L);
        assertThat(row.getResumeId()).isEqualTo(3L);
        assertThat(row.getScore()).isEqualTo(50);
        assertThat(row.getMatchedSkills()).isEqualTo("[\"Java\"]");
        assertThat(row.getMissingSkills()).isEqualTo("[\"Docker\"]");
        assertThat(row.getNearMatchedSkills()).isEqualTo("[]");
        assertThat(row.getLocationFitStatus()).isEqualTo("unknown");
    }

    @Test
    void jobSkillsAreExtractedOnceAndStoredOnTheJob() {
        Job job = job(7L, "Dev", "Java and Docker", null);
        stubHappyPath(job, resume("t", "[]"));

        service.matchJob(1L, 7L);

        assertThat(job.getSkills()).isEqualTo("[\"Java\",\"Docker\"]");
        verify(jobs).save(job);
    }

    @Test
    void storedJobSkillsAreReusedWithoutExtractingAgain() {
        Job job = job(7L, "Dev", "Java and Docker", null);
        job.setSkills("[\"Go\"]");
        stubHappyPath(job, resume("t", "[]"));

        MatchResponse result = service.matchJob(1L, 7L);

        assertThat(result.missingSkills()).containsExactly("Go");
        verify(jobs, never()).save(any());
    }

    @Test
    void anOlderResumeWithoutSkillsIsExtractedAndSaved() {
        Job job = job(7L, "Dev", "Needs Python", null);
        Resume old = resume("I know Python well", null);
        stubHappyPath(job, old);

        MatchResponse result = service.matchJob(1L, 7L);

        assertThat(result.score()).isEqualTo(100);
        assertThat(old.getSkills()).isEqualTo("[\"Python\"]");
        verify(resumes).save(old);
    }

    @Test
    void matchWithoutAProfileIsProfileNotFound() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.matchJob(1L, 7L)).isInstanceOf(ProfileNotFoundException.class);
        verify(matches, never()).save(any());
    }

    @Test
    void anUnknownJobIsNotFound() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(jobs.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.matchJob(1L, 7L)).isInstanceOf(JobNotFoundException.class);
    }

    @Test
    void anotherProfilesPastedJobIsNotFound() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(jobs.findById(7L)).thenReturn(Optional.of(job(7L, "Dev", "Java", 99L)));

        assertThatThrownBy(() -> service.matchJob(1L, 7L)).isInstanceOf(JobNotFoundException.class);
        verify(matches, never()).save(any());
    }

    @Test
    void matchWithoutABaseResumeIsRejected() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(jobs.findById(7L)).thenReturn(Optional.of(job(7L, "Dev", "Java", null)));
        when(resumes.findByProfileIdAndIsBaseTrue(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.matchJob(1L, 7L)).isInstanceOf(NoBaseResumeException.class);
        verify(matches, never()).save(any());
    }

    @Test
    void matchIfResumeReturnsNullWithoutABaseResume() {
        when(resumes.findByProfileIdAndIsBaseTrue(10L)).thenReturn(Optional.empty());

        assertThat(service.matchIfResume(profile, job(7L, "Dev", "Java", 10L))).isNull();
        verify(matches, never()).save(any());
    }

    @Test
    void matchIfResumeComputesWhenThereIsABaseResume() {
        Job job = job(7L, "Dev", "Java", 10L);
        stubHappyPath(job, resume("t", "[\"Java\"]"));

        MatchResponse result = service.matchIfResume(profile, job);

        assertThat(result).isNotNull();
        assertThat(result.score()).isEqualTo(100);
    }

    @Test
    void getStoredReturnsTheSavedMatch() {
        Job job = job(7L, "Dev", "Java", null);
        JobMatch row = new JobMatch(10L, 7L);
        row.setScore(40);
        row.setMatchedSkills("[\"Java\"]");
        row.setNearMatchedSkills("[{\"jobSkill\":\"React\",\"resumeSkill\":\"React Native\"}]");
        row.setLocationFitStatus("partial");
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(jobs.findById(7L)).thenReturn(Optional.of(job));
        when(matches.findByProfileIdAndJobId(10L, 7L)).thenReturn(Optional.of(row));

        MatchResponse result = service.getStored(1L, 7L);

        assertThat(result.score()).isEqualTo(40);
        assertThat(result.matchedSkills()).containsExactly("Java");
        assertThat(result.nearMatchedSkills()).containsExactly(new NearMatch("React", "React Native"));
        assertThat(result.locationFit().status()).isEqualTo("partial");
    }

    @Test
    void getStoredIsNotFoundWhenNothingWasSaved() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(jobs.findById(7L)).thenReturn(Optional.of(job(7L, "Dev", "Java", null)));
        when(matches.findByProfileIdAndJobId(10L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStored(1L, 7L)).isInstanceOf(MatchNotFoundException.class);
    }

    @Test
    void getStoredHidesAnotherProfilesPastedJob() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(jobs.findById(7L)).thenReturn(Optional.of(job(7L, "Dev", "Java", 99L)));

        assertThatThrownBy(() -> service.getStored(1L, 7L)).isInstanceOf(JobNotFoundException.class);
        verify(matches, never()).findByProfileIdAndJobId(any(), any());
    }

    @Test
    void getStoredWithoutAProfileIsProfileNotFound() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStored(1L, 7L)).isInstanceOf(ProfileNotFoundException.class);
    }
}
