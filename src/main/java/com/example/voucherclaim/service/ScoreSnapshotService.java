package com.example.voucherclaim.service;

import java.util.OptionalLong;

/** Application boundary for reading and updating trusted priority-score snapshots. */
public interface ScoreSnapshotService {
    /** Reads the trusted priority score snapshot used to order a user's claim. */
    OptionalLong get(String campaignId, String userId);

    /** Stores or replaces the trusted score snapshot supplied by the internal score boundary. */
    void put(String campaignId, String userId, long score);
}
