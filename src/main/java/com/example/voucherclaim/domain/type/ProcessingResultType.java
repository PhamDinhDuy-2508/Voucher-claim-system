package com.example.voucherclaim.domain.type;

/**
 * Worker outcomes persisted with the durable claim request. Non-terminal progress is represented
 * by ClaimRequestStatus; a terminal request keeps one of these outcomes for status reads.
 *
 * <pre>
 * PROCESSING --> CREATED | REPLAYED | SOLD_OUT | CAMPAIGN_NOT_ACTIVE
 * PROCESSING --BUSY--> RETRY_WAIT
 * </pre>
 */
public enum ProcessingResultType {
    /** This request created and committed a new claim. */
    CREATED,
    /** The campaign-and-user operation already owns the committed claim. */
    REPLAYED,
    /** No claimable physical slot remains. */
    SOLD_OUT,
    /** Processing could not complete safely now; the client may retry the same campaign and user. */
    BUSY,
    /** The campaign is outside its ACTIVE claimable state/window. */
    CAMPAIGN_NOT_ACTIVE
}
