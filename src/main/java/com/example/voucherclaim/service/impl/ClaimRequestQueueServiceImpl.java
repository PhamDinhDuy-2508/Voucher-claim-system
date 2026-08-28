package com.example.voucherclaim.service.impl;

import com.example.voucherclaim.domain.type.QueueAdmissionResult;
import com.example.voucherclaim.exception.ServiceException;
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
    public void materialize(String requestId) {
        PriorityRequest request = requestService.prepareForEnqueue(requestId).orElse(null);
        if (request == null) {
            log.debug("Priority materialization skipped requestId={} reason=not-eligible", requestId);
            return;
        }

        // Redis is called outside the database transaction. If either side fails, the durable
        // PENDING/QUEUED row remains discoverable and ZADD NX makes the next attempt harmless.
        QueueAdmissionResult result = priorityQueue.enqueue(request);
        if (result == QueueAdmissionResult.FULL) {
            log.warn("Priority queue full requestId={} campaignId={}",
                    requestId, request.getCampaignId());
            throw ServiceException.busy("Priority queue is full");
        }
        requestService.markQueued(requestId);
        log.debug("Priority request materialized requestId={} campaignId={} score={} result={}",
                requestId, request.getCampaignId(), request.getScoreSnapshot(), result);
    }

    /** Periodic safety net for lost Kafka delivery, lost Redis data and expired worker leases. */
    @Override
    public void recoverDueRequests() {
        var requestIds = requestService.findRecoverableIds();
        if (!requestIds.isEmpty()) {
            log.info("Starting claim request recovery count={}", requestIds.size());
        }
        for (String requestId : requestIds) {
            try {
                materialize(requestId);
            } catch (RuntimeException failure) {
                log.warn("Cannot recover durable claim request {} yet", requestId, failure);
            }
        }
    }
}
