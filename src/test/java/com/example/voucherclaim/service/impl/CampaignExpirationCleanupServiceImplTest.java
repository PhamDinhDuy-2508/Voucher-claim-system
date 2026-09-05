package com.example.voucherclaim.service.impl;

import com.example.voucherclaim.config.AppProperties;
import com.example.voucherclaim.redis.CampaignAvailabilityCache;
import com.example.voucherclaim.repository.CampaignRepository;
import com.example.voucherclaim.repository.SlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CampaignExpirationCleanupServiceImplTest {
    private final CampaignRepository campaignRepository = mock(CampaignRepository.class);
    private final SlotRepository slotRepository = mock(SlotRepository.class);
    private final CampaignAvailabilityCache availabilityCache = mock(CampaignAvailabilityCache.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final AppProperties properties = mock(AppProperties.class);
    private final AppProperties.ExpirationCleanup cleanup =
            new AppProperties.ExpirationCleanup(20, 5_000);
    private final CampaignExpirationCleanupServiceImpl service =
            new CampaignExpirationCleanupServiceImpl(
                    campaignRepository,
                    slotRepository,
                    availabilityCache,
                    transactionTemplate,
                    properties
            );

    @BeforeEach
    void executeTransactionCallbacksSynchronously() {
        when(properties.getExpirationCleanup()).thenReturn(cleanup);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    @Test
    void marksExpiredCampaignEndedBeforeDeletingOneSlotBatch() {
        when(campaignRepository.findExpirationCleanupCandidates(any(), any(Pageable.class)))
                .thenReturn(List.of("campaign-1"));
        when(campaignRepository.markEndedIfExpired(eq("campaign-1"), any(Instant.class)))
                .thenReturn(1);
        when(slotRepository.deleteBatchByCampaignId("campaign-1", 5_000)).thenReturn(5_000);

        service.processExpiredCampaigns();

        verify(campaignRepository).markEndedIfExpired(eq("campaign-1"), any(Instant.class));
        verify(slotRepository).deleteBatchByCampaignId("campaign-1", 5_000);
        verify(availabilityCache).evict("campaign-1");
    }

    @Test
    void continuesDeletingSlotsForCampaignAlreadyEnded() {
        when(campaignRepository.findExpirationCleanupCandidates(any(), any(Pageable.class)))
                .thenReturn(List.of("campaign-1"));
        when(campaignRepository.markEndedIfExpired(eq("campaign-1"), any(Instant.class)))
                .thenReturn(0);
        when(slotRepository.deleteBatchByCampaignId("campaign-1", 5_000)).thenReturn(17);

        service.processExpiredCampaigns();

        verify(slotRepository).deleteBatchByCampaignId("campaign-1", 5_000);
    }
}
