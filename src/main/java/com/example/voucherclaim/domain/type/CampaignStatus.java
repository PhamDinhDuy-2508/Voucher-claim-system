package com.example.voucherclaim.domain.type;

/**
 * Persisted campaign state machine:
 *
 * <pre>
 * DRAFT --activate--> ACTIVE --last inventory consumed--> SOLD_OUT
 *                         |
 *                         +--campaign window ends--------> ENDED
 * </pre>
 *
 * SOLD_OUT and ENDED are terminal. The current service implements activation and
 * sold-out transitions; ENDED is reserved for the campaign-expiry/reconciliation job.
 */
public enum CampaignStatus {
    /** Initial state. Campaign configuration exists but claims are rejected. */
    DRAFT,
    /** Claimable state after all inventory slots have been materialized. */
    ACTIVE,
    /** Terminal state reached when the final physical inventory slot is consumed. */
    SOLD_OUT,
    /** Terminal state reached when the campaign claim window has elapsed. */
    ENDED
}
