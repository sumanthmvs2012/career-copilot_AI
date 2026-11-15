package com.careercopilot.jobs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String website;

    @Column(name = "careers_url", length = 500)
    private String careersUrl;

    @Column(name = "ats_type", length = 30)
    private String atsType;

    @Column(name = "board_token", length = 200)
    private String boardToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Company() {
        // Required by JPA.
    }

    public Company(String name) {
        this.name = name;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getCareersUrl() {
        return careersUrl;
    }

    public void setCareersUrl(String careersUrl) {
        this.careersUrl = careersUrl;
    }

    public String getAtsType() {
        return atsType;
    }

    public void setAtsType(String atsType) {
        this.atsType = atsType;
    }

    public String getBoardToken() {
        return boardToken;
    }

    public void setBoardToken(String boardToken) {
        this.boardToken = boardToken;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
