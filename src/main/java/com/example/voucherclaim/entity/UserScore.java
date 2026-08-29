package com.example.voucherclaim.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/** Durable source of truth for the global score used by priority admission. */
@Entity
@Table(name = "user_score")
public class UserScore {
    @Id
    @Column(name = "user_id", nullable = false, length = 16)
    private String userId;

    @Column(nullable = false)
    private long score;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserScore() {
    }

    public UserScore(String userId, long score) {
        this.userId = userId;
        this.score = score;
    }

    /** Replaces the current global priority score; JPA increments version on commit. */
    public void updateScore(long score) {
        this.score = score;
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

    public String getUserId() { return userId; }
    public long getScore() { return score; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
