package com.example.voucherclaim.domain.type;

/**
 * Durable activation-job state machine:
 *
 * <pre>
 * PENDING ----acquire lease----> PROCESSING ----batch remains----> PENDING
 *    ^                              |   |
 *    |                              |   +----final batch---------> COMPLETED
 *    +---------retry due------------+
 *                                   +--------failure-------------> RETRY_WAIT
 * </pre>
 */
public enum CampaignActivationJobStatus {
    /** Ready for a worker to acquire. */
    PENDING,
    /** Owned by one worker until its lease expires. */
    PROCESSING,
    /** Temporarily delayed after a failed batch. */
    RETRY_WAIT,
    /** All slots exist and the campaign is ACTIVE. */
    COMPLETED
}
