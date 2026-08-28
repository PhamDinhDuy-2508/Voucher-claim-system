package com.example.voucherclaim.service;

import com.example.voucherclaim.model.PriorityRequest;

/** Processes one claim request admitted by the priority scheduler. */
public interface ClaimWorker {
    /** Acquires durable ownership and drives one queued request to terminal state or retry wait. */
    void process(PriorityRequest request);
}
