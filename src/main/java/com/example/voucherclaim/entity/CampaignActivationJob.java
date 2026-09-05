package com.example.voucherclaim.entity;

import com.example.voucherclaim.domain.type.CampaignActivationJobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;

/** Durable cursor and lease for asynchronous campaign slot materialization. */
@Entity
@Table(name = "campaign_activation_job")
public class CampaignActivationJob {
    @Id
    @Column(name = "campaign_id", nullable = false, length = 36)
    private String campaignId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CampaignActivationJobStatus status;

    @Column(name = "next_slot_id", nullable = false)
    private long nextSlotId;

    @Column(name = "total_quantity", nullable = false)
    private long totalQuantity;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "lease_owner", length = 128)
    private String leaseOwner;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CampaignActivationJob() {
    }

    public CampaignActivationJob(String campaignId, long totalQuantity, Instant now) {
        this.campaignId = campaignId;
        this.status = CampaignActivationJobStatus.PENDING;
        this.nextSlotId = 1;
        this.totalQuantity = totalQuantity;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Acquires or recovers a lease without allowing two active owners. */
    public boolean acquireLease(String owner, Instant now, Instant until) {
        boolean ready = (status == CampaignActivationJobStatus.PENDING
                || status == CampaignActivationJobStatus.RETRY_WAIT)
                && !nextAttemptAt.isAfter(now);
        boolean expired = status == CampaignActivationJobStatus.PROCESSING
                && leaseUntil != null && !leaseUntil.isAfter(now);
        if (!ready && !expired) {
            return false;
        }
        status = CampaignActivationJobStatus.PROCESSING;
        leaseOwner = owner;
        leaseUntil = until;
        attempt++;
        updatedAt = now;
        return true;
    }

    /** Advances the durable cursor and releases the lease after one committed batch. */
    public boolean completeBatch(String owner, long followingSlotId, Instant now) {
        requireOwner(owner);
        nextSlotId = followingSlotId;
        leaseOwner = null;
        leaseUntil = null;
        nextAttemptAt = now;
        updatedAt = now;
        if (nextSlotId > totalQuantity) {
            status = CampaignActivationJobStatus.COMPLETED;
            return true;
        }
        status = CampaignActivationJobStatus.PENDING;
        return false;
    }

    /** Releases a failed lease and delays the next durable retry. */
    public void markRetry(String owner, Instant retryAt, Instant now) {
        requireOwner(owner);
        status = CampaignActivationJobStatus.RETRY_WAIT;
        leaseOwner = null;
        leaseUntil = null;
        nextAttemptAt = retryAt;
        updatedAt = now;
    }

    /** Stops slot materialization when the campaign lifecycle has already ended. */
    public void cancel(String owner, Instant now) {
        requireOwner(owner);
        status = CampaignActivationJobStatus.CANCELED;
        leaseOwner = null;
        leaseUntil = null;
        updatedAt = now;
    }

    public boolean isOwnedBy(String owner) {
        return status == CampaignActivationJobStatus.PROCESSING
                && Objects.equals(leaseOwner, owner);
    }

    private void requireOwner(String owner) {
        if (!isOwnedBy(owner)) {
            throw new IllegalStateException("Activation job lease is not owned by this worker");
        }
    }

    public String getCampaignId() { return campaignId; }
    public CampaignActivationJobStatus getStatus() { return status; }
    public long getNextSlotId() { return nextSlotId; }
    public long getTotalQuantity() { return totalQuantity; }
    public int getAttempt() { return attempt; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLeaseOwner() { return leaseOwner; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
