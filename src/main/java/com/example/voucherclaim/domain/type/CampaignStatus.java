package com.example.voucherclaim.domain.type;

/**
 * Persisted campaign state machine:
 *
 * <pre>
 * DRAFT --activate request--> ACTIVATING --all slots ready--> ACTIVE
 *                                                              |
 *                                                              +--> SOLD_OUT
 *                                                              +--> ENDED
 * </pre>
 *
 * SOLD_OUT and ENDED are terminal. The expiration cleanup worker owns transitions
 * to ENDED and removes unused slot rows in bounded batches.
 */
public enum CampaignStatus {
    /** Initial state. Campaign configuration exists but claims are rejected. */
    DRAFT,
    /** Durable activation job is materializing inventory slots in bounded transactions. */
    ACTIVATING,
    /** Claimable state after all inventory slots have been materialized. */
    ACTIVE,
    /** Terminal state reached when the final physical inventory slot is consumed. */
    SOLD_OUT,
    /** Terminal state reached when the campaign claim window has elapsed. */
    ENDED
}
