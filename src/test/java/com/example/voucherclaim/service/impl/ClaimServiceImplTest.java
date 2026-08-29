package com.example.voucherclaim.service.impl;

import com.example.voucherclaim.config.AppProperties;
import com.example.voucherclaim.domain.RequestIds;
import com.example.voucherclaim.domain.type.ClaimStatus;
import com.example.voucherclaim.domain.type.ProcessingResultType;
import com.example.voucherclaim.entity.ClaimRequest;
import com.example.voucherclaim.entity.VoucherClaim;
import com.example.voucherclaim.model.PriorityRequest;
import com.example.voucherclaim.model.ProcessingResult;
import com.example.voucherclaim.redis.ClaimResultCache;
import com.example.voucherclaim.redis.RequestResultStore;
import com.example.voucherclaim.repository.ClaimRepository;
import com.example.voucherclaim.service.ClaimRequestQueueService;
import com.example.voucherclaim.service.ClaimRequestService;
import com.example.voucherclaim.service.ScoreSnapshotService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimServiceImplTest {
    private static final String CAMPAIGN_ID = "019c6fa6-5e22-7abc-9123-abcdef123456";
    private static final String USER_ID = "2000000000000001";

    @Mock ClaimResultCache claimResultCache;
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
                claimResultCache, requestResultStore, scoreSnapshots, claimRequestService,
                claimRequestQueueService, claimRepository, properties);
    }

    @Test
    void returnsCachedClaimWithoutReadingDurableRequestOrClaimTable() {
        VoucherClaim claim = claim();
        when(claimResultCache.get(CAMPAIGN_ID, USER_ID)).thenReturn(Optional.of(claim));

        ProcessingResult result = service.claim(CAMPAIGN_ID, USER_ID);

        assertThat(result.getType()).isEqualTo(ProcessingResultType.REPLAYED);
        assertThat(result.getClaim()).isSameAs(claim);
        verifyNoInteractions(claimRequestService, claimRepository, scoreSnapshots,
                claimRequestQueueService, requestResultStore);
    }

    @Test
    void newOperationDoesNotQueryVoucherClaimAfterCacheAndRequestMiss() {
        String requestId = RequestIds.forClaim(CAMPAIGN_ID, USER_ID);
        ClaimRequest admitted = request(requestId);
        ProcessingResult workerResult = ProcessingResult.failure(
                requestId, ProcessingResultType.SOLD_OUT, "Sold out");
        when(claimResultCache.get(CAMPAIGN_ID, USER_ID)).thenReturn(Optional.empty());
        when(claimRequestService.find(requestId)).thenReturn(Optional.empty());
        when(scoreSnapshots.get(USER_ID)).thenReturn(OptionalLong.of(900));
        when(claimRequestService.submit(any(PriorityRequest.class))).thenReturn(admitted);
        when(properties.getPriority()).thenReturn(priorityProperties);
        when(priorityProperties.getResultWaitTimeout()).thenReturn(Duration.ofSeconds(1));
        when(priorityProperties.getResultPollInterval()).thenReturn(Duration.ofMillis(1));
        when(requestResultStore.get(CAMPAIGN_ID, requestId)).thenReturn(Optional.of(workerResult));

        ProcessingResult result = service.claim(CAMPAIGN_ID, USER_ID);

        assertThat(result).isSameAs(workerResult);
        verify(claimRepository, never()).findByCampaignIdAndUserId(any(), any());
        verify(claimRepository, never()).findById(any());
        var order = inOrder(claimRequestService, claimRequestQueueService);
        order.verify(claimRequestService).submit(any(PriorityRequest.class));
        order.verify(claimRequestQueueService).materialize(requestId);
    }

    @Test
    void terminalRequestUsesClaimIdAndWarmsTheDedicatedCache() {
        String requestId = RequestIds.forClaim(CAMPAIGN_ID, USER_ID);
        VoucherClaim claim = claim();
        ClaimRequest request = request(requestId);
        request.complete(ProcessingResultType.CREATED, claim.getClaimId(), null, Instant.now());
        when(claimResultCache.get(CAMPAIGN_ID, USER_ID)).thenReturn(Optional.empty());
        when(claimRequestService.find(requestId)).thenReturn(Optional.of(request));
        when(claimRepository.findById(claim.getClaimId())).thenReturn(Optional.of(claim));

        ProcessingResult result = service.claim(CAMPAIGN_ID, USER_ID);

        assertThat(result.getClaim()).isSameAs(claim);
        verify(claimResultCache).put(claim);
        verify(claimRepository, never()).findByCampaignIdAndUserId(any(), any());
        verifyNoInteractions(scoreSnapshots, claimRequestQueueService, requestResultStore);
    }

    private ClaimRequest request(String requestId) {
        return new ClaimRequest(requestId, CAMPAIGN_ID, USER_ID, 900, 10, Instant.now());
    }

    private VoucherClaim claim() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        return new VoucherClaim(
                UUID.randomUUID(), CAMPAIGN_ID, USER_ID, "VCH-TEST", ClaimStatus.ISSUED,
                900, now, now.plusSeconds(86_400));
    }
}
