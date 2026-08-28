package com.example.voucherclaim.domain.type;

/**
 * Durable lifecycle of an admitted claim request.
 *
 * <pre>
 * PENDING -> QUEUED -> PROCESSING -> SUCCEEDED
 *                              \-> REJECTED
 *                    PROCESSING -> RETRY_WAIT -> QUEUED
 * </pre>
 *
 * Redis may lose a QUEUED member, but MySQL keeps this state so the recovery watcher can
 * materialize the member again. A lease prevents a stale duplicate member from executing twice.
 */
public enum ClaimRequestStatus {
    /** Persisted with its ClaimRequested outbox event, but not confirmed in Redis yet. */
    PENDING,
    /** Materialized in the Redis priority index. */
    QUEUED,
    /** Owned by one worker until leaseUntil. */
    PROCESSING,
    /** Retryable processing failed; it becomes eligible at nextAttemptAt. */
    RETRY_WAIT,
    /** A claim was created or the same idempotent operation was replayed. */
    SUCCEEDED,
    /** A terminal business rule rejected the request. */
    REJECTED
}
