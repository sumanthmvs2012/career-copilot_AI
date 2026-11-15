package com.careercopilot.applications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.careercopilot.jobs.Company;
import com.careercopilot.jobs.CompanyRepository;
import com.careercopilot.jobs.Job;
import com.careercopilot.jobs.JobRepository;
import com.careercopilot.profiles.Profile;
import com.careercopilot.profiles.ProfileNotFoundException;
import com.careercopilot.profiles.ProfileRepository;
import com.careercopilot.resumes.Resume;
import com.careercopilot.resumes.ResumeNotFoundException;
import com.careercopilot.resumes.ResumeRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock
    private ProfileRepository profiles;

    @Mock
    private ApplicationRepository applications;

    @Mock
    private JobRepository jobs;

    @Mock
    private CompanyRepository companies;

    @Mock
    private ResumeRepository resumes;

    private ApplicationService service;
    private Profile profile;

    @BeforeEach
    void setUp() {
        service = new ApplicationService(profiles, applications, jobs, companies, resumes);
        profile = new Profile(1L);
        ReflectionTestUtils.setField(profile, "id", 10L);
    }

    private static Job job(long id, Long ownerProfileId, Long companyId) {
        Job job = new Job("greenhouse", "Java Dev", "Description");
        ReflectionTestUtils.setField(job, "id", id);
        job.setCreatedByProfileId(ownerProfileId);
        job.setCompanyId(companyId);
        return job;
    }

    private static Application saved(long id, long jobId) {
        Application application = new Application(10L, jobId);
        ReflectionTestUtils.setField(application, "id", id);
        return application;
    }

    private void stubSave() {
        when(applications.saveAndFlush(any(Application.class))).thenAnswer(invocation -> {
            Application a = invocation.getArgument(0);
            ReflectionTestUtils.setField(a, "id", 5L);
            return a;
        });
    }

    @Test
    void createRequiresAProfile() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L, new ApplicationCreateRequest(3L, null, null, null)))
                .isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    void createDefaultsToDraftWithCompanyName() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(jobs.findById(3L)).thenReturn(Optional.of(job(3L, null, 7L)));
        when(applications.existsByProfileIdAndJobId(10L, 3L)).thenReturn(false);
        when(companies.findAllById(List.of(7L))).thenReturn(List.of(new Company("Stripe")));
        stubSave();

        ApplicationResponse response = service.create(1L, new ApplicationCreateRequest(3L, null, null, "Hello"));

        assertThat(response.status()).isEqualTo("draft");
        assertThat(response.appliedAt()).isNull();
        assertThat(response.submittedBy()).isEqualTo("user");
        assertThat(response.jobTitle()).isEqualTo("Java Dev");
        assertThat(response.emailSent()).isFalse();
        assertThat(response.emailSentAt()).isNull();
        assertThat(response.coverLetter()).isEqualTo("Hello");
    }

    @Test
    void createAppliedSetsSubmittedAtAndKeepsOwnPastedJobAndResume() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(jobs.findById(3L)).thenReturn(Optional.of(job(3L, 10L, null)));
        when(resumes.findByIdAndProfileId(4L, 10L)).thenReturn(Optional.of(new Resume(10L, "k", "cv.pdf", "t", "x")));
        stubSave();

        ApplicationResponse response = service.create(1L, new ApplicationCreateRequest(3L, 4L, "applied", null));

        assertThat(response.status()).isEqualTo("applied");
        assertThat(response.appliedAt()).isNotNull();
        assertThat(response.resumeId()).isEqualTo(4L);
        assertThat(response.company()).isNull();
    }

    @Test
    void createRejectsAnotherProfilesPastedJob() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(jobs.findById(3L)).thenReturn(Optional.of(job(3L, 99L, null)));

        assertThatThrownBy(() -> service.create(1L, new ApplicationCreateRequest(3L, null, null, null)))
                .isInstanceOf(ApplicationNotFoundException.class);
        verify(applications, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsAMissingJob() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(jobs.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L, new ApplicationCreateRequest(3L, null, null, null)))
                .isInstanceOf(ApplicationNotFoundException.class);
    }

    @Test
    void createRejectsAResumeOfAnotherProfile() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(jobs.findById(3L)).thenReturn(Optional.of(job(3L, null, null)));
        when(resumes.findByIdAndProfileId(4L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L, new ApplicationCreateRequest(3L, 4L, null, null)))
                .isInstanceOf(ResumeNotFoundException.class);
    }

    @Test
    void createRejectsADuplicate() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(jobs.findById(3L)).thenReturn(Optional.of(job(3L, null, null)));
        when(applications.existsByProfileIdAndJobId(10L, 3L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(1L, new ApplicationCreateRequest(3L, null, null, null)))
                .isInstanceOf(ApplicationAlreadyExistsException.class);
    }

    @Test
    void createMapsAUniqueConstraintRaceToAlreadyExists() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(jobs.findById(3L)).thenReturn(Optional.of(job(3L, null, null)));
        when(applications.saveAndFlush(any(Application.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.create(1L, new ApplicationCreateRequest(3L, null, null, null)))
                .isInstanceOf(ApplicationAlreadyExistsException.class);
    }

    @Test
    void listIsEmptyWithoutAProfileOrApplications() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.empty());
        assertThat(service.list(1L)).isEmpty();

        when(profiles.findByUserId(2L)).thenReturn(Optional.of(profile));
        when(applications.findByProfileIdOrderByCreatedAtDescIdDesc(10L)).thenReturn(List.of());
        assertThat(service.list(2L)).isEmpty();
    }

    @Test
    void listBatchLoadsJobsAndCompanies() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(applications.findByProfileIdOrderByCreatedAtDescIdDesc(10L))
                .thenReturn(List.of(saved(2L, 3L), saved(1L, 4L)));
        when(jobs.findAllById(List.of(3L, 4L))).thenReturn(List.of(job(3L, null, 7L), job(4L, null, null)));
        Company company = new Company("Stripe");
        ReflectionTestUtils.setField(company, "id", 7L);
        when(companies.findAllById(List.of(7L))).thenReturn(List.of(company));

        List<ApplicationResponse> result = service.list(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).company()).isEqualTo("Stripe");
        assertThat(result.get(1).company()).isNull();
        verify(jobs).findAllById(List.of(3L, 4L));
    }

    @Test
    void updateSetsAppliedAtOnlyTheFirstTime() {
        Application application = saved(5L, 3L);
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(applications.findByIdAndProfileId(5L, 10L)).thenReturn(Optional.of(application));
        when(applications.saveAndFlush(application)).thenReturn(application);
        when(jobs.findById(3L)).thenReturn(Optional.of(job(3L, null, null)));

        ApplicationResponse first = service.update(1L, 5L, new ApplicationUpdateRequest("applied", null));
        Instant firstAppliedAt = first.appliedAt();
        ApplicationResponse second = service.update(1L, 5L, new ApplicationUpdateRequest("interviewing", "Letter"));

        assertThat(firstAppliedAt).isNotNull();
        assertThat(second.appliedAt()).isEqualTo(firstAppliedAt);
        assertThat(second.status()).isEqualTo("interviewing");
        assertThat(second.coverLetter()).isEqualTo("Letter");
    }

    @Test
    void updateReturnsNotFoundForAnotherUsersApplicationOrNoProfile() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(applications.findByIdAndProfileId(5L, 10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(1L, 5L, new ApplicationUpdateRequest("applied", null)))
                .isInstanceOf(ApplicationNotFoundException.class);

        when(profiles.findByUserId(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(2L, 5L, new ApplicationUpdateRequest("applied", null)))
                .isInstanceOf(ApplicationNotFoundException.class);
    }

    @Test
    void requestRecordsTrackWhichFieldsArePresent() {
        assertThat(new ApplicationUpdateRequest(null, null).isAnyFieldPresent()).isFalse();
        assertThat(new ApplicationUpdateRequest(null, "x").isAnyFieldPresent()).isTrue();
    }
}
