package com.example.voucherclaim.entity;

import com.example.voucherclaim.domain.type.ClaimStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "voucher_claim",
        uniqueConstraints = @UniqueConstraint(name = "uk_voucher_code", columnNames = "voucher_code")
)
public class VoucherClaim {
    @Id
    @Column(name = "claim_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID claimId;

    @Column(name = "campaign_id", nullable = false, length = 36)
    private String campaignId;

    @Column(name = "user_id", nullable = false, length = 16)
    private String userId;

    @Column(name = "voucher_code", nullable = false, length = 128)
    private String voucherCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ClaimStatus status;

    @Column(name = "priority_score_snapshot", nullable = false)
    private long priorityScoreSnapshot;

    @Column(name = "claimed_at", nullable = false)
    private Instant claimedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public VoucherClaim() {
    }

    public VoucherClaim(
            UUID claimId,
            String campaignId,
            String userId,
            String voucherCode,
            ClaimStatus status,
            long priorityScoreSnapshot,
            Instant claimedAt,
            Instant expiresAt
    ) {
        this.claimId = claimId;
        this.campaignId = campaignId;
        this.userId = userId;
        this.voucherCode = voucherCode;
        this.status = status;
        this.priorityScoreSnapshot = priorityScoreSnapshot;
        this.claimedAt = claimedAt;
        this.expiresAt = expiresAt;
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

    public UUID getClaimId() { return claimId; }
    public void setClaimId(UUID claimId) { this.claimId = claimId; }
    public String getCampaignId() { return campaignId; }
    public void setCampaignId(String campaignId) { this.campaignId = campaignId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getVoucherCode() { return voucherCode; }
    public void setVoucherCode(String voucherCode) { this.voucherCode = voucherCode; }
    public ClaimStatus getStatus() { return status; }
    public void setStatus(ClaimStatus status) { this.status = status; }
    public long getPriorityScoreSnapshot() { return priorityScoreSnapshot; }
    public void setPriorityScoreSnapshot(long priorityScoreSnapshot) { this.priorityScoreSnapshot = priorityScoreSnapshot; }
    public Instant getClaimedAt() { return claimedAt; }
    public void setClaimedAt(Instant claimedAt) { this.claimedAt = claimedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
