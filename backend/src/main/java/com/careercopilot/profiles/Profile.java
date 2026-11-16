package com.careercopilot.profiles;

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
@Table(name = "profiles")
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(length = 200)
    private String headline;

    @Column(length = 200)
    private String location;

    @Column(name = "remote_ok", nullable = false)
    private boolean remoteOk;

    @Column(name = "auto_submit_enabled", nullable = false)
    private boolean autoSubmitEnabled;

    @Column(name = "last_agent_run_at")
    private Instant lastAgentRunAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Profile() {
        // Required by JPA.
    }

    public Profile(Long userId) {
        this.userId = userId;
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

    public Long getUserId() {
        return userId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public boolean isRemoteOk() {
        return remoteOk;
    }

    public void setRemoteOk(boolean remoteOk) {
        this.remoteOk = remoteOk;
    }

    public boolean isAutoSubmitEnabled() {
        return autoSubmitEnabled;
    }

    public void setAutoSubmitEnabled(boolean autoSubmitEnabled) {
        this.autoSubmitEnabled = autoSubmitEnabled;
    }

    public Instant getLastAgentRunAt() {
        return lastAgentRunAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
