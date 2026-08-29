package com.example.voucherclaim.service.impl;

import com.example.voucherclaim.domain.type.QueueAdmissionResult;
import com.example.voucherclaim.model.PriorityRequest;
import com.example.voucherclaim.redis.PriorityQueueService;
import com.example.voucherclaim.service.ClaimRequestQueueService;
import com.example.voucherclaim.service.ClaimRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ClaimRequestQueueServiceImpl implements ClaimRequestQueueService {
    private static final Logger log = LoggerFactory.getLogger(ClaimRequestQueueServiceImpl.class);
    private final ClaimRequestService requestService;
    private final PriorityQueueService priorityQueue;

    public ClaimRequestQueueServiceImpl(ClaimRequestService requestService,
                                        PriorityQueueService priorityQueue) {
        this.requestService = requestService;
        this.priorityQueue = priorityQueue;
    }

    /** Builds the disposable Redis priority entry from its durable MySQL source. */
    @Override
    public QueueAdmissionResult materialize(String requestId) {
        log.debug("Priority materialization started requestId={}", requestId);
        PriorityRequest request = requestService.prepareForEnqueue(requestId).orElse(null);
        if (request == null) {
            log.debug("Priority materialization skipped requestId={} reason=not-eligible", requestId);
            return QueueAdmissionResult.SKIPPED;
        }

        // The caller invokes Redis after the admission transaction commits. If this operation or
        // the following status update fails, MySQL remains discoverable and ZADD NX is repeatable.
        log.debug("Enqueueing claim into Redis Sorted Set requestId={} campaignId={} userId={} score={}",
                requestId, request.getCampaignId(), request.getUserId(), request.getScoreSnapshot());
        QueueAdmissionResult result = priorityQueue.enqueue(request);
        if (result == QueueAdmissionResult.FULL) {
            // The durable PENDING row remains recoverable. Queue capacity must not turn a
            // committed asynchronous admission into an HTTP failure.
            log.warn("Priority queue full; durable request remains pending requestId={} campaignId={}",
                    requestId, request.getCampaignId());
            return result;
        }
        // durable PENDING row and this Redis member exist, the API can return 202 QUEUED.
        // The worker can lease PENDING directly; recovery records QUEUED only when it repairs.
        log.debug("Priority request materialized requestId={} campaignId={} userId={} score={} result={}",
                requestId, request.getCampaignId(), request.getUserId(),
                request.getScoreSnapshot(), result);
        return result;
    }

    /** Periodic safety net for failed direct materialization, lost Redis data and expired leases. */
    @Override
    public void recoverDueRequests() {
        var requestIds = requestService.findRecoverableIds();
        if (!requestIds.isEmpty()) {
            log.info("Starting claim request recovery count={}", requestIds.size());
        }
        for (String requestId : requestIds) {
            try {
                QueueAdmissionResult result = materialize(requestId);
                if (result == QueueAdmissionResult.ADDED
                        || result == QueueAdmissionResult.ALREADY_PENDING) {
                    // This write is off the HTTP path. It advances nextAttemptAt so the
                    // watcher does not repeatedly scan a member already present in Redis.
                    requestService.markQueued(requestId);
                }
            } catch (RuntimeException failure) {
                log.warn("Cannot recover durable claim request {} yet", requestId, failure);
            }
        }
    }
}
