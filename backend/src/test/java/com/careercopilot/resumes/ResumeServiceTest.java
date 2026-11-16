package com.careercopilot.resumes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.careercopilot.common.storage.StorageException;
import com.careercopilot.common.storage.StorageService;
import com.careercopilot.matches.SkillExtractor;
import com.careercopilot.profiles.Profile;
import com.careercopilot.profiles.ProfileNotFoundException;
import com.careercopilot.profiles.ProfileRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ResumeServiceTest {

    @Mock
    private ProfileRepository profiles;

    @Mock
    private ResumeRepository resumes;

    @Mock
    private StorageService storage;

    private ResumeService service;
    private Profile profile;

    @BeforeEach
    void setUp() {
        service = new ResumeService(profiles, resumes, storage, new TextExtractor(), new SkillExtractor());
        profile = new Profile(1L);
        ReflectionTestUtils.setField(profile, "id", 10L);
    }

    private static MockMultipartFile pdfFile(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/pdf", content);
    }

    @Test
    void uploadThrowsWhenTheUserHasNoProfile() throws IOException {
        when(profiles.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upload(1L, pdfFile("cv.pdf", TestFiles.pdf("Hello"))))
                .isInstanceOf(ProfileNotFoundException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void uploadRejectsAWrongExtension() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.upload(1L,
                new MockMultipartFile("file", "cv.txt", "text/plain", "hello".getBytes())))
                .isInstanceOf(InvalidResumeFileException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void uploadRejectsAFileWithNoExtension() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.upload(1L, pdfFile("cv", "%PDF-1.4".getBytes())))
                .isInstanceOf(InvalidResumeFileException.class);
    }

    @Test
    void uploadRejectsWrongMagicBytesEvenWithAPdfName() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.upload(1L, pdfFile("cv.pdf", "just some text".getBytes())))
                .isInstanceOf(InvalidResumeFileException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void uploadRejectsADocxThatIsNotAZip() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.upload(1L, pdfFile("cv.docx", "%PDF-1.4 text".getBytes())))
                .isInstanceOf(InvalidResumeFileException.class);
    }

    @Test
    void uploadRejectsAnEmptyFile() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.upload(1L, pdfFile("cv.pdf", new byte[0])))
                .isInstanceOf(InvalidResumeFileException.class);
    }

    @Test
    void uploadRejectsAFileOverFiveMegabytes() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy("%PDF-".getBytes(), 0, big, 0, 5);

        assertThatThrownBy(() -> service.upload(1L, pdfFile("cv.pdf", big)))
                .isInstanceOf(ResumeTooLargeException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void firstUploadCreatesTheBaseResume() throws IOException {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(resumes.findByProfileIdAndIsBaseTrue(10L)).thenReturn(Optional.empty());
        when(resumes.save(any(Resume.class))).thenAnswer(inv -> inv.getArgument(0));

        Resume result = service.upload(1L, pdfFile("../My CV.PDF", TestFiles.pdf("Hello resume")));

        assertThat(result.isBase()).isTrue();
        assertThat(result.getProfileId()).isEqualTo(10L);
        assertThat(result.getFileName()).isEqualTo("My CV.PDF");
        assertThat(result.getContentType()).isEqualTo(TextExtractor.PDF);
        assertThat(result.getParsedText()).contains("Hello resume");
        assertThat(result.getSkills()).isEqualTo("[]");
        assertThat(result.getFilePath()).matches("resumes/10/[0-9a-f-]{36}\\.pdf");
        verify(storage).store(eq(result.getFilePath()), any(InputStream.class), anyLong());
        verify(storage, never()).delete(anyString());
    }

    @Test
    void secondUploadCreatesANewBaseAndDemotesTheFirst() throws IOException {
        Resume existing = new Resume(10L, "resumes/10/old.pdf", "old.pdf", TextExtractor.PDF, "old text");
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(resumes.countByProfileIdAndBaseResumeIdIsNull(10L)).thenReturn(1L);
        when(resumes.findByProfileIdAndIsBaseTrue(10L)).thenReturn(Optional.of(existing));
        when(resumes.save(any(Resume.class))).thenAnswer(inv -> inv.getArgument(0));

        Resume result = service.upload(1L, pdfFile("new.pdf", TestFiles.pdf("Fresh text")));

        assertThat(result).isNotSameAs(existing);
        assertThat(result.isBase()).isTrue();
        assertThat(result.getFileName()).isEqualTo("new.pdf");
        assertThat(result.getParsedText()).contains("Fresh text");
        assertThat(existing.isBase()).isFalse();
        assertThat(existing.getFilePath()).isEqualTo("resumes/10/old.pdf");
        verify(storage).store(eq(result.getFilePath()), any(InputStream.class), anyLong());
        verify(storage, never()).delete(anyString());
    }

    @Test
    void thirdUploadIsRefusedBeforeAnythingIsStored() throws IOException {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(resumes.countByProfileIdAndBaseResumeIdIsNull(10L)).thenReturn(2L);

        assertThatThrownBy(() -> service.upload(1L, pdfFile("cv.pdf", TestFiles.pdf("Hello"))))
                .isInstanceOf(ResumeLimitReachedException.class)
                .hasMessage("You already have 2 resumes. Delete one to upload another.");
        verifyNoInteractions(storage);
        verify(resumes, never()).save(any(Resume.class));
    }

    @Test
    void deletingTheBasePromotesTheNewestRemainingResume() {
        Resume base = resumeWithId(5L, "resumes/10/a.pdf", true);
        Resume other = resumeWithId(6L, "resumes/10/b.pdf", false);
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(resumes.findByIdAndProfileId(5L, 10L)).thenReturn(Optional.of(base));
        when(resumes.findFirstByProfileIdAndBaseResumeIdIsNullOrderByCreatedAtDesc(10L))
                .thenReturn(Optional.of(other));

        service.delete(1L, 5L);

        verify(resumes).delete(base);
        assertThat(other.isBase()).isTrue();
        verify(resumes).save(other);
        verify(storage).delete("resumes/10/a.pdf");
    }

    @Test
    void deletingTheOnlyResumeLeavesNone() {
        Resume base = resumeWithId(5L, "resumes/10/a.pdf", true);
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(resumes.findByIdAndProfileId(5L, 10L)).thenReturn(Optional.of(base));
        when(resumes.findFirstByProfileIdAndBaseResumeIdIsNullOrderByCreatedAtDesc(10L))
                .thenReturn(Optional.empty());

        service.delete(1L, 5L);

        verify(resumes).delete(base);
        verify(resumes, never()).save(any(Resume.class));
        verify(storage).delete("resumes/10/a.pdf");
    }

    @Test
    void deletingAnUploadAlsoDeletesItsTailoredCopiesAndFiles() {
        Resume base = resumeWithId(5L, "resumes/10/a.pdf", true);
        Resume copy = resumeWithId(8L, "resumes/10/t.txt", false);
        copy.setBaseResumeId(5L);
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(resumes.findByIdAndProfileId(5L, 10L)).thenReturn(Optional.of(base));
        when(resumes.findByProfileIdAndBaseResumeId(10L, 5L)).thenReturn(List.of(copy));

        service.delete(1L, 5L);

        verify(resumes).deleteAll(List.of(copy));
        verify(resumes).delete(base);
        verify(storage).delete("resumes/10/t.txt");
        verify(storage).delete("resumes/10/a.pdf");
    }

    @Test
    void deletingATailoredCopyNeverTouchesTheBaseOrOtherCopies() {
        Resume copy = resumeWithId(8L, "resumes/10/t.txt", false);
        copy.setBaseResumeId(5L);
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(resumes.findByIdAndProfileId(8L, 10L)).thenReturn(Optional.of(copy));

        service.delete(1L, 8L);

        verify(resumes).delete(copy);
        verify(resumes, never()).deleteAll(any());
        verify(resumes, never()).findByProfileIdAndBaseResumeId(anyLong(), anyLong());
        verify(resumes, never()).save(any(Resume.class));
        verify(storage).delete("resumes/10/t.txt");
    }

    @Test
    void deletingANonBaseResumeKeepsTheBase() {
        Resume other = resumeWithId(6L, "resumes/10/b.pdf", false);
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(resumes.findByIdAndProfileId(6L, 10L)).thenReturn(Optional.of(other));

        service.delete(1L, 6L);

        verify(resumes).delete(other);
        verify(resumes, never()).save(any(Resume.class));
    }

    @Test
    void deletingAnotherProfilesResumeIsNotFound() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(resumes.findByIdAndProfileId(99L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L, 99L)).isInstanceOf(ResumeNotFoundException.class);
        verify(resumes, never()).delete(any(Resume.class));
        verifyNoInteractions(storage);
    }

    @Test
    void deletingWithoutAProfileIsNotFound() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L, 5L)).isInstanceOf(ResumeNotFoundException.class);
    }

    @Test
    void aFailedFileDeleteDoesNotFailTheRequest() {
        Resume base = resumeWithId(5L, "resumes/10/a.pdf", true);
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(resumes.findByIdAndProfileId(5L, 10L)).thenReturn(Optional.of(base));
        when(resumes.findFirstByProfileIdAndBaseResumeIdIsNullOrderByCreatedAtDesc(10L))
                .thenReturn(Optional.empty());
        doThrow(new StorageException("disk error")).when(storage).delete("resumes/10/a.pdf");

        service.delete(1L, 5L);

        verify(resumes).delete(base);
    }

    private static Resume resumeWithId(Long id, String key, boolean base) {
        Resume resume = new Resume(10L, key, "cv.pdf", TextExtractor.PDF, "text");
        resume.setBase(base);
        ReflectionTestUtils.setField(resume, "id", id);
        return resume;
    }

    @Test
    void uploadRemovesTheNewFileIfSavingFails() throws IOException {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(resumes.findByProfileIdAndIsBaseTrue(10L)).thenReturn(Optional.empty());
        when(resumes.save(any(Resume.class))).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.upload(1L, pdfFile("cv.pdf", TestFiles.pdf("Hello"))))
                .isInstanceOf(IllegalStateException.class);
        verify(storage).delete(anyString());
    }

    @Test
    void getReturnsTheResumeOfTheCallersProfile() {
        Resume resume = new Resume(10L, "k", "cv.pdf", TextExtractor.PDF, "text");
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(resumes.findByIdAndProfileId(5L, 10L)).thenReturn(Optional.of(resume));

        assertThat(service.get(1L, 5L)).isSameAs(resume);
    }

    @Test
    void getForAnotherProfilesResumeIsNotFound() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(resumes.findByIdAndProfileId(99L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(1L, 99L)).isInstanceOf(ResumeNotFoundException.class);
    }

    @Test
    void getWithoutAProfileIsNotFound() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(1L, 5L)).isInstanceOf(ResumeNotFoundException.class);
    }

    @Test
    void listReturnsTheProfilesResumes() {
        Resume resume = new Resume(10L, "k", "cv.pdf", TextExtractor.PDF, "text");
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(resumes.findByProfileIdOrderByIsBaseDescCreatedAtDesc(10L)).thenReturn(List.of(resume));

        assertThat(service.list(1L)).containsExactly(resume);
    }

    @Test
    void listIsEmptyWithoutAProfile() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.empty());

        assertThat(service.list(1L)).isEmpty();
    }

    @Test
    void uploadStoresTheSkillsFoundInTheResumeText() throws IOException {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(resumes.findByProfileIdAndIsBaseTrue(10L)).thenReturn(Optional.empty());
        when(resumes.save(any(Resume.class))).thenAnswer(inv -> inv.getArgument(0));

        Resume result = service.upload(1L, pdfFile("cv.pdf", TestFiles.pdf("Java and React developer")));

        assertThat(result.getSkills()).isEqualTo("[\"Java\",\"React\"]");
    }

    @Test
    void getFillsInSkillsForAnOlderResumeOnce() {
        Resume resume = new Resume(10L, "k", "cv.pdf", TextExtractor.PDF, "Python and Docker");
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(resumes.findByIdAndProfileId(5L, 10L)).thenReturn(Optional.of(resume));

        service.get(1L, 5L);
        service.get(1L, 5L);

        assertThat(resume.getSkills()).isEqualTo("[\"Python\",\"Docker\"]");
        verify(resumes, org.mockito.Mockito.times(1)).save(resume);
    }

    @Test
    void listFillsInSkillsForAnOlderResume() {
        Resume resume = new Resume(10L, "k", "cv.pdf", TextExtractor.PDF, "Kubernetes");
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(resumes.findByProfileIdOrderByIsBaseDescCreatedAtDesc(10L)).thenReturn(List.of(resume));

        service.list(1L);

        assertThat(resume.getSkills()).isEqualTo("[\"Kubernetes\"]");
    }
}
