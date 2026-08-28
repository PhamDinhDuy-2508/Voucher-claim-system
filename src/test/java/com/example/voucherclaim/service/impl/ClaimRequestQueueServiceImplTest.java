package com.example.voucherclaim.service.impl;

import com.example.voucherclaim.domain.type.QueueAdmissionResult;
import com.example.voucherclaim.model.PriorityRequest;
import com.example.voucherclaim.redis.PriorityQueueService;
import com.example.voucherclaim.service.ClaimRequestService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimRequestQueueServiceImplTest {
    @Test
    void marksDurableRequestQueuedAfterIdempotentRedisMaterialization() {
        ClaimRequestService requestService = mock(ClaimRequestService.class);
        PriorityQueueService priorityQueue = mock(PriorityQueueService.class);
        ClaimRequestQueueServiceImpl service = new ClaimRequestQueueServiceImpl(requestService, priorityQueue);
        PriorityRequest request = new PriorityRequest(
                "a".repeat(64), "019c6fa6-5e22-7abc-9123-abcdef123456", "2000000000000001", "idem-key", 900L);
        when(requestService.prepareForEnqueue(request.getRequestId())).thenReturn(Optional.of(request));
        when(priorityQueue.enqueue(request)).thenReturn(QueueAdmissionResult.ALREADY_PENDING);

        service.materialize(request.getRequestId());

        verify(requestService).markQueued(request.getRequestId());
    }
}
