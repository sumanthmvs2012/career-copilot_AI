package com.careercopilot.interviews;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "interview_questions")
public class InterviewQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(nullable = false, length = 1000)
    private String question;

    @Column(nullable = false, length = 20)
    private String category;

    // "position" can be a reserved word in some databases, so the column name is spelled out.
    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InterviewQuestion() {
        // Required by JPA.
    }

    public InterviewQuestion(Long profileId, Long jobId, String question, String category, int position) {
        this.profileId = profileId;
        this.jobId = jobId;
        this.question = question;
        this.category = category;
        this.position = position;
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

    public Long getJobId() {
        return jobId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getQuestion() {
        return question;
    }

    public String getCategory() {
        return category;
    }

    public int getPosition() {
        return position;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
