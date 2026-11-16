package com.careercopilot.resumes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.careercopilot.common.storage.StorageService;
import com.careercopilot.jobs.Company;
import com.careercopilot.jobs.CompanyRepository;
import com.careercopilot.jobs.Job;
import com.careercopilot.matches.JobNotFoundException;
import com.careercopilot.matches.LocationFit;
import com.careercopilot.matches.MatchAnalysis;
import com.careercopilot.matches.MatchService;
import com.careercopilot.profiles.Profile;
import com.careercopilot.profiles.ProfileNotFoundException;
import com.careercopilot.profiles.ProfileRepository;
import java.io.InputStream;
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
class ResumeTailorServiceTest {

    @Mock
    private ProfileRepository profiles;

    @Mock
    private ResumeRepository resumes;

    @Mock
    private CompanyRepository companies;

    @Mock
    private MatchService matchService;

    @Mock
    private StorageService storage;

    private ResumeTailorService service;
    private Profile profile;
    private Job job;
    private Resume original;

    @BeforeEach
    void setUp() {
        service = new ResumeTailorService(profiles, resumes, companies, matchService,
                new ResumeSuggestionService(), storage);
        profile = new Profile(1L);
        ReflectionTestUtils.setField(profile, "id", 10L);
        job = new Job("greenhouse", "Platform Engineer", "Java and Docker");
        ReflectionTestUtils.setField(job, "id", 7L);
        job.setCompanyId(3L);
        original = new Resume(10L, "resumes/10/a.pdf", "cv.pdf", "application/pdf", "Jane. I use Java.");
        ReflectionTestUtils.setField(original, "id", 5L);
        original.setSkills("[\"Java\"]");
    }

    private MatchAnalysis analysis() {
        return new MatchAnalysis(50, List.of("Java", "Docker"), List.of("Java"), List.of("Docker"), List.of(),
                List.of(), "Platform Engineer", new LocationFit("match", "ok"));
    }

    private void stubHappyPath() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(resumes.findByIdAndProfileId(5L, 10L)).thenReturn(Optional.of(original));
        when(matchService.visibleJob(profile, 7L)).thenReturn(job);
        when(matchService.analyze(profile, job, original)).thenReturn(analysis());
        Company company = new Company("Acme");
        lenient().when(companies.findById(3L)).thenReturn(Optional.of(company));
        lenient().when(resumes.save(any(Resume.class))).thenAnswer(call -> {
            Resume saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 99L);
            return saved;
        });
    }

    @Test
    void tailorSavesATextCopyAndLeavesTheOriginalAlone() {
        stubHappyPath();

        TailoredResumeResponse response = service.tailor(1L, 5L, 7L);

        assertThat(response.id()).isEqualTo(99L);
        assertThat(response.fileName()).isEqualTo("Tailored - Platform Engineer - Acme.txt");
        assertThat(response.contentType()).isEqualTo("text/plain");
        assertThat(response.isBase()).isFalse();
        assertThat(response.baseResumeId()).isEqualTo(5L);
        assertThat(response.jobId()).isEqualTo(7L);
        assertThat(response.jobTitle()).isEqualTo("Platform Engineer");
        assertThat(response.company()).isEqualTo("Acme");
        assertThat(response.skills()).containsExactly("Java");
        assertThat(response.parsedText())
                .isEqualTo("Platform Engineer\nRelevant skills: Java\n\nJane. I use Java.");
        assertThat(response.textLength()).isEqualTo(response.parsedText().length());
        assertThat(response.suggestions()).extracting(SuggestionResponse::type).contains("add_skill", "headline");
        assertThat(original.isBase()).isTrue();
        assertThat(original.getBaseResumeId()).isNull();
        assertThat(original.getParsedText()).isEqualTo("Jane. I use Java.");
        verify(resumes, never()).delete(any(Resume.class));
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).store(key.capture(), any(InputStream.class), anyLong());
        assertThat(key.getValue()).matches("resumes/10/[0-9a-f-]{36}\\.txt");
    }

    @Test
    void tailorReplacesThePreviousCopyAndItsFile() {
        stubHappyPath();
        Resume old = new Resume(10L, "resumes/10/old.txt", "x.txt", "text/plain", "old");
        when(resumes.findByProfileIdAndBaseResumeIdAndJobId(10L, 5L, 7L)).thenReturn(List.of(old));

        service.tailor(1L, 5L, 7L);

        verify(resumes).deleteAll(List.of(old));
        verify(storage).delete("resumes/10/old.txt");
    }

    @Test
    void tailorUsesTheTitleAloneWhenThereIsNoCompany() {
        stubHappyPath();
        job.setCompanyId(null);

        assertThat(service.tailor(1L, 5L, 7L).fileName()).isEqualTo("Tailored - Platform Engineer.txt");
    }

    @Test
    void tailorRemovesTheStoredFileWhenSavingFails() {
        stubHappyPath();
        doThrow(new IllegalStateException("db down")).when(resumes).save(any(Resume.class));

        assertThatThrownBy(() -> service.tailor(1L, 5L, 7L)).isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(storage).store(stored.capture(), any(InputStream.class), anyLong());
        verify(storage).delete(stored.getValue());
    }

    @Test
    void tailorNeedsAProfile() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.tailor(1L, 5L, 7L)).isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    void tailorRejectsAnotherUsersResumeAndATailoredCopy() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(resumes.findByIdAndProfileId(5L, 10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.tailor(1L, 5L, 7L)).isInstanceOf(ResumeNotFoundException.class);

        original.setBaseResumeId(4L);
        when(resumes.findByIdAndProfileId(5L, 10L)).thenReturn(Optional.of(original));
        assertThatThrownBy(() -> service.tailor(1L, 5L, 7L)).isInstanceOf(ResumeNotFoundException.class);
        verify(storage, never()).store(anyString(), any(), anyLong());
    }

    @Test
    void tailorRejectsAJobTheCallerCannotSee() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(resumes.findByIdAndProfileId(5L, 10L)).thenReturn(Optional.of(original));
        when(matchService.visibleJob(eq(profile), anyLong())).thenThrow(new JobNotFoundException());

        assertThatThrownBy(() -> service.tailor(1L, 5L, 8L)).isInstanceOf(JobNotFoundException.class);
        verify(storage, never()).store(anyString(), any(), anyLong());
    }

    @Test
    void suggestWorksForATailoredCopyToo() {
        original.setBaseResumeId(4L);
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(resumes.findByIdAndProfileId(5L, 10L)).thenReturn(Optional.of(original));
        when(matchService.visibleJob(profile, 7L)).thenReturn(job);
        when(matchService.analyze(profile, job, original)).thenReturn(analysis());

        SuggestionsResponse response = service.suggest(1L, 5L, 7L);

        assertThat(response.suggestions()).extracting(SuggestionResponse::type).contains("add_skill");
        verify(storage, never()).store(anyString(), any(), anyLong());
    }

    @Test
    void suggestNeedsAProfileAndAResumeAndAVisibleJob() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.suggest(1L, 5L, 7L)).isInstanceOf(ProfileNotFoundException.class);

        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(resumes.findByIdAndProfileId(5L, 10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.suggest(1L, 5L, 7L)).isInstanceOf(ResumeNotFoundException.class);

        when(resumes.findByIdAndProfileId(5L, 10L)).thenReturn(Optional.of(original));
        when(matchService.visibleJob(profile, 7L)).thenThrow(new JobNotFoundException());
        assertThatThrownBy(() -> service.suggest(1L, 5L, 7L)).isInstanceOf(JobNotFoundException.class);
    }

    @Test
    void fileNameStripsLineBreaksAndSlashesAndCapsTheLength() {
        Job odd = new Job("manual", "Dev/Ops\nLead", "d");

        assertThat(ResumeTailorService.fileName(odd, "A\\B")).isEqualTo("Tailored - Dev Ops Lead - A B.txt");
        Job longTitle = new Job("manual", "x".repeat(400), "d");
        assertThat(ResumeTailorService.fileName(longTitle, null)).hasSize(255).endsWith(".txt");
    }
}
