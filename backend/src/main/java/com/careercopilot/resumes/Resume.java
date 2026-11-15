package com.careercopilot.resumes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "resumes")
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "parsed_text", columnDefinition = "TEXT")
    private String parsedText;

    @Column(columnDefinition = "TEXT")
    private String skills;

    @Column(name = "is_base", nullable = false)
    private boolean isBase;

    @Column(name = "base_resume_id")
    private Long baseResumeId;

    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Resume() {
        // Required by JPA.
    }

    public Resume(Long profileId, String filePath, String fileName, String contentType, String parsedText) {
        this.profileId = profileId;
        this.filePath = filePath;
        this.fileName = fileName;
        this.contentType = contentType;
        this.parsedText = parsedText;
        this.isBase = true;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getProfileId() {
        return profileId;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getParsedText() {
        return parsedText;
    }

    public void setParsedText(String parsedText) {
        this.parsedText = parsedText;
    }

    public String getSkills() {
        return skills;
    }

    public void setSkills(String skills) {
        this.skills = skills;
    }

    public boolean isBase() {
        return isBase;
    }

    public void setBase(boolean isBase) {
        this.isBase = isBase;
    }

    public Long getBaseResumeId() {
        return baseResumeId;
    }

    public void setBaseResumeId(Long baseResumeId) {
        this.baseResumeId = baseResumeId;
    }

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
