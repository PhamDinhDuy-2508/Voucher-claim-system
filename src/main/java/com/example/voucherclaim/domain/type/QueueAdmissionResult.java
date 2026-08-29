package com.example.voucherclaim.domain.type;

/**
 * Atomic priority-queue admission outcomes:
 *
 * <pre>
 * ABSENT --enqueue--> ADDED/PENDING --same member enqueue--> ALREADY_PENDING
 * ABSENT --enqueue while queue at capacity----------------> FULL (not inserted)
 * INELIGIBLE durable state --------------------------------> SKIPPED
 * </pre>
 */
public enum QueueAdmissionResult {
    /** A new logical request was inserted into the sorted set. */
    ADDED,
    /** The same deterministic queue member is already pending. */
    ALREADY_PENDING,
    /** Capacity rejected the request without inserting it. */
    FULL,
    /** The durable request is terminal, leased, or not due for queue materialization. */
    SKIPPED
}
