package com.example.voucherclaim.domain.type;

/**
 * Terminal outcomes of one queued claim request. A missing result in Redis represents
 * PENDING; the worker writes exactly one of these outcomes and does not transition it again.
 *
 * <pre>
 * PENDING --> CREATED | REPLAYED | SOLD_OUT | BUSY | CAMPAIGN_NOT_ACTIVE
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
