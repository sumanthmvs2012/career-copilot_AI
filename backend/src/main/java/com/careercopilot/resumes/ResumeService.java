package com.careercopilot.resumes;

import com.careercopilot.common.storage.StorageException;
import com.careercopilot.common.storage.StorageService;
import com.careercopilot.profiles.Profile;
import com.careercopilot.profiles.ProfileNotFoundException;
import com.careercopilot.profiles.ProfileRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

    static final long MAX_BYTES = 5L * 1024 * 1024;
    static final int MAX_UPLOADED_RESUMES = 2;

    private final ProfileRepository profiles;
    private final ResumeRepository resumes;
    private final StorageService storage;
    private final TextExtractor extractor;

    public ResumeService(ProfileRepository profiles, ResumeRepository resumes, StorageService storage,
            TextExtractor extractor) {
        this.profiles = profiles;
        this.resumes = resumes;
        this.storage = storage;
        this.extractor = extractor;
    }

    @Transactional
    public Resume upload(Long userId, MultipartFile file) {
        Profile profile = profiles.findByUserId(userId).orElseThrow(ProfileNotFoundException::new);
        if (resumes.countByProfileIdAndBaseResumeIdIsNull(profile.getId()) >= MAX_UPLOADED_RESUMES) {
            throw new ResumeLimitReachedException();
        }
        if (file == null || file.isEmpty()) {
            throw new InvalidResumeFileException();
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResumeTooLargeException();
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ResumeUnreadableException(e);
        }
        if (content.length > MAX_BYTES) {
            throw new ResumeTooLargeException();
        }
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = extensionOf(originalName);
        String contentType = contentTypeFor(extension, content);

        String text = extractor.extract(contentType, content);
        String displayName = displayName(originalName);

        // Server-generated key; the client file name never reaches the file system.
        String key = "resumes/" + profile.getId() + "/" + UUID.randomUUID() + "." + extension;
        storage.store(key, new ByteArrayInputStream(content), content.length);

        Resume saved;
        try {
            // The newest upload becomes the base resume; the previous base stays as a normal resume.
            resumes.findByProfileIdAndIsBaseTrue(profile.getId()).ifPresent(previous -> {
                previous.setBase(false);
                resumes.save(previous);
            });
            saved = resumes.save(new Resume(profile.getId(), key, displayName, contentType, text));
        } catch (RuntimeException e) {
            storage.delete(key);
            throw e;
        }
        log.info("Resume uploaded for profile {}", profile.getId());
        return saved;
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Profile profile = profiles.findByUserId(userId).orElseThrow(ResumeNotFoundException::new);
        Resume resume = resumes.findByIdAndProfileId(id, profile.getId()).orElseThrow(ResumeNotFoundException::new);
        String key = resume.getFilePath();
        boolean wasBase = resume.isBase();

        resumes.delete(resume);
        if (wasBase) {
            resumes.findFirstByProfileIdAndBaseResumeIdIsNullOrderByCreatedAtDesc(profile.getId())
                    .filter(remaining -> !remaining.getId().equals(id))
                    .ifPresent(remaining -> {
                        remaining.setBase(true);
                        resumes.save(remaining);
                    });
        }
        try {
            storage.delete(key);
        } catch (StorageException e) {
            log.warn("Could not delete the stored file of resume {}", id);
        }
        log.info("Resume {} deleted for profile {}", id, profile.getId());
    }

    @Transactional(readOnly = true)
    public List<Resume> list(Long userId) {
        Profile profile = profiles.findByUserId(userId).orElse(null);
        if (profile == null) {
            return List.of();
        }
        return resumes.findByProfileIdOrderByIsBaseDescCreatedAtDesc(profile.getId());
    }

    @Transactional(readOnly = true)
    public Resume get(Long userId, Long id) {
        Profile profile = profiles.findByUserId(userId).orElseThrow(ResumeNotFoundException::new);
        return resumes.findByIdAndProfileId(id, profile.getId()).orElseThrow(ResumeNotFoundException::new);
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ext.equals("pdf") && !ext.equals("docx")) {
            throw new InvalidResumeFileException();
        }
        return ext;
    }

    private static String contentTypeFor(String extension, byte[] content) {
        if (extension.equals("pdf")) {
            if (!startsWith(content, new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D})) {
                throw new InvalidResumeFileException();
            }
            return TextExtractor.PDF;
        }
        if (!startsWith(content, new byte[] {0x50, 0x4B, 0x03, 0x04})) {
            throw new InvalidResumeFileException();
        }
        return TextExtractor.DOCX;
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /** Keeps only the last path segment and caps the length to the column size. */
    private static String displayName(String name) {
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).strip();
        return base.length() > 255 ? base.substring(base.length() - 255) : base;
    }
}
