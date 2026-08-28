package com.example.voucherclaim.service.impl;

import com.example.voucherclaim.config.AppProperties;
import com.example.voucherclaim.domain.RequestIds;
import com.example.voucherclaim.domain.type.ClaimStatus;
import com.example.voucherclaim.domain.type.ProcessingResultType;
import com.example.voucherclaim.entity.ClaimRequest;
import com.example.voucherclaim.entity.VoucherClaim;
import com.example.voucherclaim.model.PriorityRequest;
import com.example.voucherclaim.model.ProcessingResult;
import com.example.voucherclaim.redis.IdempotencyResultCache;
import com.example.voucherclaim.redis.RequestResultStore;
import com.example.voucherclaim.service.ClaimRequestQueueService;
import com.example.voucherclaim.service.ClaimRequestService;
import com.example.voucherclaim.service.ScoreSnapshotService;
import com.example.voucherclaim.repository.ClaimRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class ClaimServiceImplTest {
    @Mock IdempotencyResultCache idempotencyCache;
    @Mock RequestResultStore requestResultStore;
    @Mock ScoreSnapshotService scoreSnapshots;
    @Mock ClaimRequestService claimRequestService;
    @Mock ClaimRequestQueueService claimRequestQueueService;
    @Mock ClaimRepository claimRepository;
    @Mock AppProperties properties;
    @Mock AppProperties.Priority priorityProperties;

    private ClaimServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClaimServiceImpl(
                idempotencyCache,
                requestResultStore,
                scoreSnapshots,
                claimRequestService,
                claimRequestQueueService,
                claimRepository,
                properties
        );
    }

    @Test
    void returnsCachedReplayWithoutDatabaseOrQueueWork() {
        String campaignId = "019c6fa6-5e22-7abc-9123-abcdef123456";
        String userId = "2000000000000001";
        String key = "same-operation";
        VoucherClaim claim = claim(campaignId, userId, key);
        when(idempotencyCache.get(campaignId, userId, key)).thenReturn(Optional.of(claim));

        ProcessingResult result = service.claim(campaignId, userId, key);

        assertThat(result.getType()).isEqualTo(ProcessingResultType.REPLAYED);
        assertThat(result.getClaim()).isSameAs(claim);
        verifyNoInteractions(claimRepository, scoreSnapshots, claimRequestService,
                claimRequestQueueService, requestResultStore);
    }

    @Test
    void replaysDurableClaimAndWarmsCacheAfterRedisMiss() {
        String campaignId = "019c6fa6-5e22-7abc-9123-abcdef123456";
        String userId = "2000000000000001";
        String key = "same-operation";
        VoucherClaim claim = claim(campaignId, userId, key);
        when(idempotencyCache.get(campaignId, userId, key)).thenReturn(Optional.empty());
        when(claimRepository.findByCampaignIdAndUserIdAndIdempotencyKey(campaignId, userId, key))
                .thenReturn(Optional.of(claim));

        ProcessingResult result = service.claim(campaignId, userId, key);

        assertThat(result.getType()).isEqualTo(ProcessingResultType.REPLAYED);
        verify(idempotencyCache).put(claim);
        verify(claimRequestService, never()).submit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsDifferentKeyWhenUserAlreadyOwnsClaim() {
        String campaignId = "019c6fa6-5e22-7abc-9123-abcdef123456";
        String userId = "2000000000000001";
        String newKey = "new-operation";
        when(idempotencyCache.get(campaignId, userId, newKey)).thenReturn(Optional.empty());
        when(claimRepository.findByCampaignIdAndUserIdAndIdempotencyKey(campaignId, userId, newKey))
                .thenReturn(Optional.empty());
        when(claimRepository.findByCampaignIdAndUserId(campaignId, userId))
                .thenReturn(Optional.of(claim(campaignId, userId, "original-operation")));

        ProcessingResult result = service.claim(campaignId, userId, newKey);

        assertThat(result.getType()).isEqualTo(ProcessingResultType.ALREADY_CLAIMED);
        verifyNoInteractions(scoreSnapshots, claimRequestService, claimRequestQueueService,
                requestResultStore);
    }

    @Test
    void materializesDurableRequestDirectlyAfterAdmission() {
        String campaignId = "019c6fa6-5e22-7abc-9123-abcdef123456";
        String userId = "2000000000000001";
        String key = "new-operation";
        String requestId = RequestIds.forClaim(campaignId, userId, key);
        ClaimRequest durable = new ClaimRequest(
                requestId, campaignId, userId, key, 900L, 10, Instant.now());
        ProcessingResult workerResult = ProcessingResult.failure(
                requestId, ProcessingResultType.SOLD_OUT, "Sold out");

        when(idempotencyCache.get(campaignId, userId, key)).thenReturn(Optional.empty());
        when(claimRepository.findByCampaignIdAndUserIdAndIdempotencyKey(campaignId, userId, key))
                .thenReturn(Optional.empty());
        when(claimRepository.findByCampaignIdAndUserId(campaignId, userId))
                .thenReturn(Optional.empty());
        when(claimRequestService.find(requestId)).thenReturn(Optional.empty());
        when(scoreSnapshots.get(campaignId, userId)).thenReturn(OptionalLong.of(900L));
        when(claimRequestService.submit(any(PriorityRequest.class))).thenReturn(durable);
        when(properties.getPriority()).thenReturn(priorityProperties);
        when(priorityProperties.getResultWaitTimeout()).thenReturn(Duration.ofSeconds(1));
        when(requestResultStore.get(campaignId, requestId)).thenReturn(Optional.of(workerResult));

        ProcessingResult result = service.claim(campaignId, userId, key);

        assertThat(result).isSameAs(workerResult);
        var order = inOrder(claimRequestService, claimRequestQueueService);
        order.verify(claimRequestService).submit(any(PriorityRequest.class));
        order.verify(claimRequestQueueService).materialize(requestId);
    }

    private VoucherClaim claim(String campaignId, String userId, String key) {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        return new VoucherClaim(
                UUID.randomUUID(),
                campaignId,
                userId,
                "VCH-TEST",
                ClaimStatus.ISSUED,
                key,
                900,
                now,
                now.plusSeconds(86_400)
        );
    }

}
