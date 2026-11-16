package com.careercopilot.mail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/** An employer reply or a reminder for one application. Never holds an email body or snippet. */
@Entity
@Table(name = "email_logs")
public class EmailLog {

    public static final String TYPE_REPLY = "employer_reply";
    public static final String TYPE_REMINDER = "reminder";
    public static final String STATUS_RECEIVED = "received";
    public static final String STATUS_NEW = "new";
    public static final String STATUS_DISMISSED = "dismissed";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(length = 20)
    private String classification;

    @Column(length = 300)
    private String subject;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "external_message_id", length = 200)
    private String externalMessageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected EmailLog() {
        // Required by JPA.
    }

    public EmailLog(Long applicationId, String type, Instant occurredAt, String status) {
        this.applicationId = applicationId;
        this.type = type;
        this.occurredAt = occurredAt;
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public String getType() {
        return type;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getExternalMessageId() {
        return externalMessageId;
    }

    public void setExternalMessageId(String externalMessageId) {
        this.externalMessageId = externalMessageId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
