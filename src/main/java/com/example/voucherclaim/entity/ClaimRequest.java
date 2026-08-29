package com.example.voucherclaim.entity;

import com.example.voucherclaim.domain.type.ClaimRequestStatus;
import com.example.voucherclaim.domain.type.ProcessingResultType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "claim_request")
public class ClaimRequest {
    @Id
    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(name = "campaign_id", nullable = false, length = 36)
    private String campaignId;

    @Column(name = "user_id", nullable = false, length = 16)
    private String userId;

    @Column(name = "priority_score_snapshot", nullable = false)
    private long priorityScoreSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ClaimRequestStatus status;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "max_attempt", nullable = false)
    private int maxAttempt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "lease_owner", length = 128)
    private String leaseOwner;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_type", length = 32)
    private ProcessingResultType resultType;

    @Column(name = "result_message", length = 255)
    private String resultMessage;

    @Column(name = "claim_id", columnDefinition = "CHAR(36)")
    private UUID claimId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ClaimRequest() {
    }

    public ClaimRequest(String requestId, String campaignId, String userId,
                        long priorityScoreSnapshot, int maxAttempt, Instant now) {
        this.requestId = requestId;
        this.campaignId = campaignId;
        this.userId = userId;
        this.priorityScoreSnapshot = priorityScoreSnapshot;
        this.status = ClaimRequestStatus.PENDING;
        this.maxAttempt = maxAttempt;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }


    /** Marks the derived Redis representation as present; this is safe to repeat. */
    public void markQueued(Instant nextRecoveryAt, Instant now) {
        if (status == ClaimRequestStatus.PENDING || status == ClaimRequestStatus.RETRY_WAIT
                || status == ClaimRequestStatus.QUEUED) {
            status = ClaimRequestStatus.QUEUED;
            nextAttemptAt = nextRecoveryAt;
            updatedAt = now;
        }
    }

    /** Acquires a short database lease before any inventory transaction starts. */
    public boolean acquireLease(String owner, Instant now, Instant until) {
        // QUEUED means Redis already selected this member, so queueRecheckDelay must not delay
        // the worker. PENDING and RETRY_WAIT still respect their due time during recovery.
        if (!canAcquireLease(now) || attempt >= maxAttempt) {
            return false;
        }
        status = ClaimRequestStatus.PROCESSING;
        attempt++;
        leaseOwner = owner;
        leaseUntil = until;
        updatedAt = now;
        return true;
    }

    /** Releases a failed attempt for delayed retry; Redis is repopulated after the due time. */
    public void markRetryWait(Instant nextAttempt, Instant now) {
        status = ClaimRequestStatus.RETRY_WAIT;
        nextAttemptAt = nextAttempt;
        leaseOwner = null;
        leaseUntil = null;
        updatedAt = now;
    }

    /** Persists the terminal result so it survives Redis loss and HTTP retries. */
    public void complete(ProcessingResultType type, UUID durableClaimId, String message, Instant now) {
        resultType = type;
        claimId = durableClaimId;
        resultMessage = message;
        status = type == ProcessingResultType.CREATED || type == ProcessingResultType.REPLAYED
                ? ClaimRequestStatus.SUCCEEDED : ClaimRequestStatus.REJECTED;
        leaseOwner = null;
        leaseUntil = null;
        updatedAt = now;
    }

    /** Fences a late worker so it cannot overwrite the outcome written by a newer lease owner. */
    public boolean completeIfOwned(String owner, ProcessingResultType type,
                                   UUID durableClaimId, String message, Instant now) {
        if (status != ClaimRequestStatus.PROCESSING || !Objects.equals(leaseOwner, owner)) {
            return false;
        }
        complete(type, durableClaimId, message, now);
        return true;
    }

    /** Applies retry only while the caller still owns the current processing lease. */
    public boolean retryIfOwned(String owner, Instant nextAttempt, Instant now) {
        if (status != ClaimRequestStatus.PROCESSING || !Objects.equals(leaseOwner, owner)) {
            return false;
        }
        markRetryWait(nextAttempt, now);
        return true;
    }

    /** Recovers a worker whose lease expired before it could persist an outcome. */
    public boolean recoverExpiredLease(Instant now) {
        if (status != ClaimRequestStatus.PROCESSING || leaseUntil == null || !leaseUntil.isBefore(now)) {
            return false;
        }
        markRetryWait(now, now);
        return true;
    }

    public boolean isReady(Instant now) {
        return (status == ClaimRequestStatus.PENDING || status == ClaimRequestStatus.QUEUED
                || status == ClaimRequestStatus.RETRY_WAIT) && !nextAttemptAt.isAfter(now);
    }

    private boolean canAcquireLease(Instant now) {
        return status == ClaimRequestStatus.QUEUED
                || ((status == ClaimRequestStatus.PENDING || status == ClaimRequestStatus.RETRY_WAIT)
                && !nextAttemptAt.isAfter(now));
    }

    public boolean isTerminal() {
        return status == ClaimRequestStatus.SUCCEEDED || status == ClaimRequestStatus.REJECTED;
    }

    public String getRequestId() { return requestId; }
    public String getCampaignId() { return campaignId; }
    public String getUserId() { return userId; }
    public long getPriorityScoreSnapshot() { return priorityScoreSnapshot; }
    public ClaimRequestStatus getStatus() { return status; }
    public int getAttempt() { return attempt; }
    public int getMaxAttempt() { return maxAttempt; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLeaseOwner() { return leaseOwner; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public ProcessingResultType getResultType() { return resultType; }
    public String getResultMessage() { return resultMessage; }
    public UUID getClaimId() { return claimId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
