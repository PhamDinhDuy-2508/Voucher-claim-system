package com.example.voucherclaim.service;

import com.example.voucherclaim.domain.type.QueueAdmissionResult;

public interface ClaimRequestQueueService {
    /** Rebuilds one eligible durable claim request as an idempotent Redis priority member. */
    QueueAdmissionResult materialize(String requestId);

    /** Finds due or lease-expired requests in MySQL and rematerializes them in bounded batches. */
    void recoverDueRequests();
}
