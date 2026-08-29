package com.example.voucherclaim.service;

import com.example.voucherclaim.domain.type.QueueAdmissionResult;
import com.example.voucherclaim.model.PriorityRequest;

public interface ClaimRequestQueueService {
    /** Adds an already-committed request to Redis without reading or locking MySQL again. */
    QueueAdmissionResult materialize(PriorityRequest request);

    /** Finds due or lease-expired requests in MySQL and rematerializes them in bounded batches. */
    void recoverDueRequests();
}
