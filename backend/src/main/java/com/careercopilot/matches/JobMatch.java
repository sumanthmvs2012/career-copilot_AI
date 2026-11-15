package com.careercopilot.matches;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "job_matches")
public class JobMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "resume_id")
    private Long resumeId;

    @Column(nullable = false)
    private int score;

    @Column(name = "matched_skills", columnDefinition = "TEXT")
    private String matchedSkills;

    @Column(name = "missing_skills", columnDefinition = "TEXT")
    private String missingSkills;

    @Column(name = "near_matched_skills", columnDefinition = "TEXT")
    private String nearMatchedSkills;

    @Column(name = "missing_keywords", columnDefinition = "TEXT")
    private String missingKeywords;

    @Column(name = "suggested_headline", length = 200)
    private String suggestedHeadline;

    @Column(name = "location_fit_status", nullable = false, length = 20)
    private String locationFitStatus = "unknown";

    @Column(name = "location_fit_note", length = 300)
    private String locationFitNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JobMatch() {
        // Required by JPA.
    }

    public JobMatch(Long profileId, Long jobId) {
        this.profileId = profileId;
        this.jobId = jobId;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getProfileId() {
        return profileId;
    }

    public Long getJobId() {
        return jobId;
    }

    public Long getResumeId() {
        return resumeId;
    }

    public void setResumeId(Long resumeId) {
        this.resumeId = resumeId;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getMatchedSkills() {
        return matchedSkills;
    }

    public void setMatchedSkills(String matchedSkills) {
        this.matchedSkills = matchedSkills;
    }

    public String getMissingSkills() {
        return missingSkills;
    }

    public void setMissingSkills(String missingSkills) {
        this.missingSkills = missingSkills;
    }

    public String getNearMatchedSkills() {
        return nearMatchedSkills;
    }

    public void setNearMatchedSkills(String nearMatchedSkills) {
        this.nearMatchedSkills = nearMatchedSkills;
    }

    public String getMissingKeywords() {
        return missingKeywords;
    }

    public void setMissingKeywords(String missingKeywords) {
        this.missingKeywords = missingKeywords;
    }

    public String getSuggestedHeadline() {
        return suggestedHeadline;
    }

    public void setSuggestedHeadline(String suggestedHeadline) {
        this.suggestedHeadline = suggestedHeadline;
    }

    public String getLocationFitStatus() {
        return locationFitStatus;
    }

    public void setLocationFitStatus(String locationFitStatus) {
        this.locationFitStatus = locationFitStatus;
    }

    public String getLocationFitNote() {
        return locationFitNote;
    }

    public void setLocationFitNote(String locationFitNote) {
        this.locationFitNote = locationFitNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
