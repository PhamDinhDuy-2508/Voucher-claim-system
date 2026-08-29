package com.example.voucherclaim.service;

import java.util.OptionalLong;

/** Application boundary for reading and updating trusted priority-score snapshots. */
public interface ScoreSnapshotService {
    /** Reads the user's current global score from Redis or the durable MySQL fallback. */
    OptionalLong get(String userId);

    /** Persists a user score in MySQL before refreshing its Redis cache entry. */
    void put(String userId, long score);
}
