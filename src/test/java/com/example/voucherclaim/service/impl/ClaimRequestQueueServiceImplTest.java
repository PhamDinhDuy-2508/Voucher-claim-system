package com.example.voucherclaim.service.impl;

import com.example.voucherclaim.domain.type.QueueAdmissionResult;
import com.example.voucherclaim.model.PriorityRequest;
import com.example.voucherclaim.redis.PriorityQueueService;
import com.example.voucherclaim.service.ClaimRequestService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimRequestQueueServiceImplTest {
    @Test
    void returnsImmediatelyAfterIdempotentRedisMaterialization() {
        ClaimRequestService requestService = mock(ClaimRequestService.class);
        PriorityQueueService priorityQueue = mock(PriorityQueueService.class);
        ClaimRequestQueueServiceImpl service = new ClaimRequestQueueServiceImpl(requestService, priorityQueue);
        PriorityRequest request = new PriorityRequest(
                "a".repeat(64), "019c6fa6-5e22-7abc-9123-abcdef123456", "2000000000000001", 900L);
        when(requestService.prepareForEnqueue(request.getRequestId())).thenReturn(Optional.of(request));
        when(priorityQueue.enqueue(request)).thenReturn(QueueAdmissionResult.ALREADY_PENDING);

        QueueAdmissionResult result = service.materialize(request.getRequestId());

        org.assertj.core.api.Assertions.assertThat(result)
                .isEqualTo(QueueAdmissionResult.ALREADY_PENDING);
        verify(requestService, never()).markQueued(request.getRequestId());
    }

    @Test
    void keepsDurableRequestPendingWhenRedisQueueIsFull() {
        ClaimRequestService requestService = mock(ClaimRequestService.class);
        PriorityQueueService priorityQueue = mock(PriorityQueueService.class);
        ClaimRequestQueueServiceImpl service = new ClaimRequestQueueServiceImpl(requestService, priorityQueue);
        PriorityRequest request = new PriorityRequest(
                "b".repeat(64), "019c6fa6-5e22-7abc-9123-abcdef123456", "2000000000000002", 500L);
        when(requestService.prepareForEnqueue(request.getRequestId())).thenReturn(Optional.of(request));
        when(priorityQueue.enqueue(request)).thenReturn(QueueAdmissionResult.FULL);

        QueueAdmissionResult result = service.materialize(request.getRequestId());

        org.assertj.core.api.Assertions.assertThat(result).isEqualTo(QueueAdmissionResult.FULL);
        verify(requestService, never()).markQueued(request.getRequestId());
    }

    @Test
    void recoveryMarksAnExistingRedisMemberQueuedOutsideTheHttpPath() {
        ClaimRequestService requestService = mock(ClaimRequestService.class);
        PriorityQueueService priorityQueue = mock(PriorityQueueService.class);
        ClaimRequestQueueServiceImpl service = new ClaimRequestQueueServiceImpl(requestService, priorityQueue);
        PriorityRequest request = new PriorityRequest(
                "c".repeat(64), "019c6fa6-5e22-7abc-9123-abcdef123456", "2000000000000003", 700L);
        when(requestService.findRecoverableIds()).thenReturn(List.of(request.getRequestId()));
        when(requestService.prepareForEnqueue(request.getRequestId())).thenReturn(Optional.of(request));
        when(priorityQueue.enqueue(request)).thenReturn(QueueAdmissionResult.ALREADY_PENDING);

        service.recoverDueRequests();

        verify(requestService).markQueued(request.getRequestId());
    }
}
