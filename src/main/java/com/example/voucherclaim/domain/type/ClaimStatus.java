package com.example.voucherclaim.domain.type;

/**
 * Persisted claim state machine for the current claim-only scope:
 *
 * <pre>
 * [not created] --slot consumed + transaction committed--> ISSUED (terminal)
 * </pre>
 *
 * Redemption, expiration and cancellation states belong to a future voucher-use flow
 * and are deliberately not modelled by this service yet.
 */
public enum ClaimStatus {
    /** Voucher ownership was durably issued to the user. */
    ISSUED
}
