package com.example.voucherclaim.service;

public interface ClaimRequestQueueService {
    /** Rebuilds one eligible durable claim request as an idempotent Redis priority member. */
    void materialize(String requestId);

    /** Finds due or lease-expired requests in MySQL and rematerializes them in bounded batches. */
    void recoverDueRequests();
}
