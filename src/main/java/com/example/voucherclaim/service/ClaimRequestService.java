package com.example.voucherclaim.service;

import com.example.voucherclaim.entity.ClaimRequest;
import com.example.voucherclaim.model.PriorityRequest;
import com.example.voucherclaim.model.ProcessingResult;

import java.util.List;
import java.util.Optional;

public interface ClaimRequestService {
    /** Persists a new durable PENDING request, or returns the existing idempotent request. */
    ClaimRequest submit(PriorityRequest request);

    /** Reads durable request state for idempotent retry and terminal-result reconstruction. */
    Optional<ClaimRequest> find(String requestId);

    /** Locks and validates a request before its Redis priority member is materialized. */
    Optional<PriorityRequest> prepareForEnqueue(String requestId);

    /** Lets recovery record an existing Redis member and defer its next repair scan. */
    void markQueued(String requestId);

    /** Attempts to acquire the processing lease; false means another worker/state owns the request. */
    boolean acquireLease(String requestId, String owner);

    /** Persists a terminal outcome and its rejection notification atomically when lease-owned. */
    void complete(String requestId, String leaseOwner, ProcessingResult result);

    /** Schedules a transiently failed request for retry, fenced by the current lease owner. */
    void retry(String requestId, String leaseOwner);

    /** Returns a bounded ordered set of due requests and expired processing leases. */
    List<String> findRecoverableIds();
}
