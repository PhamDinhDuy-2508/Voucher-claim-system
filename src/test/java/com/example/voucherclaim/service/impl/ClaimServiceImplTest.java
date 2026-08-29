package com.example.voucherclaim.service.impl;

import com.example.voucherclaim.domain.RequestIds;
import com.example.voucherclaim.domain.type.ClaimRequestStatus;
import com.example.voucherclaim.domain.type.ClaimStatus;
import com.example.voucherclaim.domain.type.CampaignStatus;
import com.example.voucherclaim.domain.type.ProcessingResultType;
import com.example.voucherclaim.domain.type.QueueAdmissionResult;
import com.example.voucherclaim.entity.ClaimRequest;
import com.example.voucherclaim.entity.VoucherClaim;
import com.example.voucherclaim.model.ClaimOperationResult;
import com.example.voucherclaim.model.PriorityRequest;
import com.example.voucherclaim.redis.ClaimResultCache;
import com.example.voucherclaim.repository.ClaimRepository;
import com.example.voucherclaim.service.ClaimRequestQueueService;
import com.example.voucherclaim.service.ClaimRequestService;
import com.example.voucherclaim.service.ScoreSnapshotService;
import com.example.voucherclaim.service.CampaignAvailabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    @Mock ScoreSnapshotService scoreSnapshots;
    @Mock ClaimRequestService claimRequestService;
    @Mock ClaimRequestQueueService claimRequestQueueService;
    @Mock ClaimRepository claimRepository;
    @Mock CampaignAvailabilityService campaignAvailabilityService;

    private ClaimServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClaimServiceImpl(
                claimResultCache, scoreSnapshots, claimRequestService,
                claimRequestQueueService, claimRepository, campaignAvailabilityService);
    }

    @Test
    void returnsCachedClaimWithoutReadingDurableRequestOrClaimTable() {
        VoucherClaim claim = claim();
        when(claimResultCache.get(CAMPAIGN_ID, USER_ID)).thenReturn(Optional.of(claim));

        ClaimOperationResult result = service.claim(CAMPAIGN_ID, USER_ID);

        assertThat(result.getStatus()).isEqualTo(ClaimRequestStatus.SUCCEEDED);
        assertThat(result.getResultType()).isEqualTo(ProcessingResultType.REPLAYED);
        assertThat(result.getClaim()).isSameAs(claim);
        verifyNoInteractions(claimRequestService, claimRepository, scoreSnapshots,
                claimRequestQueueService);
    }

    @Test
    void newOperationDoesNotQueryVoucherClaimAfterCacheAndRequestMiss() {
        String requestId = RequestIds.forClaim(CAMPAIGN_ID, USER_ID);
        ClaimRequest admitted = request(requestId);
        when(claimResultCache.get(CAMPAIGN_ID, USER_ID)).thenReturn(Optional.empty());
        when(claimRequestService.find(requestId)).thenReturn(Optional.empty());
        when(campaignAvailabilityService.get(CAMPAIGN_ID))
                .thenReturn(new com.example.voucherclaim.model.CampaignAvailability(
                        CAMPAIGN_ID, CampaignStatus.ACTIVE, true));
        when(scoreSnapshots.get(USER_ID)).thenReturn(OptionalLong.of(900));
        when(claimRequestService.submit(any(PriorityRequest.class))).thenReturn(admitted);
        when(claimRequestQueueService.materialize(any(PriorityRequest.class)))
                .thenReturn(QueueAdmissionResult.ADDED);

        ClaimOperationResult result = service.claim(CAMPAIGN_ID, USER_ID);

        assertThat(result.getRequestId()).isEqualTo(requestId);
        assertThat(result.getStatus()).isEqualTo(ClaimRequestStatus.QUEUED);
        assertThat(result.getClaim()).isNull();
        verify(claimRepository, never()).findByCampaignIdAndUserId(any(), any());
        verify(claimRepository, never()).findById(any());
        var order = inOrder(claimRequestService, claimRequestQueueService);
        order.verify(claimRequestService).submit(any(PriorityRequest.class));
        order.verify(claimRequestQueueService).materialize(any(PriorityRequest.class));
        verify(claimRequestService, never()).prepareForEnqueue(requestId);
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

        ClaimOperationResult result = service.claim(CAMPAIGN_ID, USER_ID);

        assertThat(result.getStatus()).isEqualTo(ClaimRequestStatus.SUCCEEDED);
        assertThat(result.getClaim()).isSameAs(claim);
        verify(claimResultCache).put(claim);
        verify(claimRepository, never()).findByCampaignIdAndUserId(any(), any());
        verifyNoInteractions(scoreSnapshots, claimRequestQueueService);
    }

    @Test
    void redisFailureStillReturnsDurablePendingAdmission() {
        String requestId = RequestIds.forClaim(CAMPAIGN_ID, USER_ID);
        ClaimRequest admitted = request(requestId);
        when(claimResultCache.get(CAMPAIGN_ID, USER_ID)).thenReturn(Optional.empty());
        when(claimRequestService.find(requestId)).thenReturn(Optional.empty());
        when(campaignAvailabilityService.get(CAMPAIGN_ID))
                .thenReturn(new com.example.voucherclaim.model.CampaignAvailability(
                        CAMPAIGN_ID, CampaignStatus.ACTIVE, true));
        when(scoreSnapshots.get(USER_ID)).thenReturn(OptionalLong.of(900));
        when(claimRequestService.submit(any(PriorityRequest.class))).thenReturn(admitted);
        when(claimRequestQueueService.materialize(any(PriorityRequest.class)))
                .thenThrow(new IllegalStateException("Redis unavailable"));

        ClaimOperationResult result = service.claim(CAMPAIGN_ID, USER_ID);

        assertThat(result.getStatus()).isEqualTo(ClaimRequestStatus.PENDING);
        assertThat(result.isTerminal()).isFalse();
    }

    @Test
    void rejectsSoldOutCampaignBeforeCreatingDurableRequest() {
        String requestId = RequestIds.forClaim(CAMPAIGN_ID, USER_ID);
        when(claimResultCache.get(CAMPAIGN_ID, USER_ID)).thenReturn(Optional.empty());
        when(claimRequestService.find(requestId)).thenReturn(Optional.empty());
        when(campaignAvailabilityService.get(CAMPAIGN_ID))
                .thenReturn(new com.example.voucherclaim.model.CampaignAvailability(
                        CAMPAIGN_ID, CampaignStatus.SOLD_OUT, false));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.claim(CAMPAIGN_ID, USER_ID))
                .isInstanceOf(com.example.voucherclaim.exception.ServiceException.class)
                .hasMessage("Campaign inventory is sold out");
        verify(claimRequestService, never()).submit(any(PriorityRequest.class));
        verifyNoInteractions(scoreSnapshots, claimRequestQueueService);
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
