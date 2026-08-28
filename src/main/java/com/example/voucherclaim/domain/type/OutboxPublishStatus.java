package com.example.voucherclaim.domain.type;

/**
 * Outbox delivery state machine:
 *
 * <pre>
 * PENDING --notification accepted--> PUBLISHED
 * PENDING --delivery failed--------> PENDING (retryCount + 1)
 * PENDING --max retries reached----> DEAD_LETTER
 * </pre>
 */
public enum OutboxPublishStatus {
    /** Event is eligible for the next publisher poll. */
    PENDING,
    /** Notification Service accepted the event; terminal state. */
    PUBLISHED,
    /** Delivery exhausted the configured retry budget; terminal until manual replay. */
    DEAD_LETTER
}
