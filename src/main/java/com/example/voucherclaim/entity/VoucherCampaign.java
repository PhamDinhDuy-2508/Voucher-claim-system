package com.example.voucherclaim.entity;

import com.example.voucherclaim.domain.type.CampaignStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "voucher_campaign")
public class VoucherCampaign {
    @Id
    @Column(name = "campaign_id", nullable = false, length = 36)
    private String campaignId;

    @Column(name = "merchant_id", nullable = false, length = 16)
    private String merchantId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "discount_type", nullable = false, length = 32)
    private String discountType;

    @Column(name = "discount_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountValue;

    @Column(name = "total_quantity", nullable = false)
    private long totalQuantity;

    @Column(name = "unallocated_quantity", nullable = false)
    private long unallocatedQuantity;

    @Column(name = "priority_window_ms", nullable = false)
    private int priorityWindowMs;

    @Column(name = "priority_policy_version", nullable = false, length = 64)
    private String priorityPolicyVersion;

    @Column(name = "creation_idempotency_key", nullable = false, length = 128)
    private String creationIdempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CampaignStatus status;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "voucher_expires_at", nullable = false)
    private Instant voucherExpiresAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VoucherCampaign() {
    }

    public VoucherCampaign(
            String campaignId,
            String merchantId,
            String name,
            String discountType,
            BigDecimal discountValue,
            long totalQuantity,
            long unallocatedQuantity,
            int priorityWindowMs,
            String priorityPolicyVersion,
            String creationIdempotencyKey,
            CampaignStatus status,
            Instant startAt,
            Instant endAt,
            Instant voucherExpiresAt
    ) {
        this.campaignId = campaignId;
        this.merchantId = merchantId;
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.totalQuantity = totalQuantity;
        this.unallocatedQuantity = unallocatedQuantity;
        this.priorityWindowMs = priorityWindowMs;
        this.priorityPolicyVersion = priorityPolicyVersion;
        this.creationIdempotencyKey = creationIdempotencyKey;
        this.status = status;
        this.startAt = startAt;
        this.endAt = endAt;
        this.voucherExpiresAt = voucherExpiresAt;
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

    public String getCampaignId() { return campaignId; }
    public String getMerchantId() { return merchantId; }
    public String getName() { return name; }
    public String getDiscountType() { return discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public long getTotalQuantity() { return totalQuantity; }
    public long getUnallocatedQuantity() { return unallocatedQuantity; }
    public int getPriorityWindowMs() { return priorityWindowMs; }
    public String getPriorityPolicyVersion() { return priorityPolicyVersion; }
    public String getCreationIdempotencyKey() { return creationIdempotencyKey; }
    public CampaignStatus getStatus() { return status; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public Instant getVoucherExpiresAt() { return voucherExpiresAt; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void activate() {
        status = CampaignStatus.ACTIVE;
        unallocatedQuantity = 0;
    }

    /** Starts durable slot materialization without making the campaign claimable. */
    public void startActivation() {
        status = CampaignStatus.ACTIVATING;
    }

    public void markSoldOut() {
        status = CampaignStatus.SOLD_OUT;
    }
}
