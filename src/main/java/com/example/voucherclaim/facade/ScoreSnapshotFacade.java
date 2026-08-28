package com.example.voucherclaim.facade;

import com.example.voucherclaim.model.request.ScoreSnapshotRequest;

/** Trusted API boundary for publishing user priority-score snapshots. */
public interface ScoreSnapshotFacade {
    void put(String internalToken, ScoreSnapshotRequest request);
}
