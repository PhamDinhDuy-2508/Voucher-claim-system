package com.example.voucherclaim.service.impl;

import com.example.voucherclaim.domain.type.CampaignStatus;
import com.example.voucherclaim.entity.VoucherCampaign;
import com.example.voucherclaim.model.CampaignAvailability;
import com.example.voucherclaim.redis.CampaignAvailabilityCache;
import com.example.voucherclaim.repository.CampaignRepository;
import com.example.voucherclaim.repository.SlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignAvailabilityServiceImplTest {
    private static final String CAMPAIGN_ID = "019c6fa6-5e22-7abc-9123-abcdef123456";

    @Mock CampaignAvailabilityCache cache;
    @Mock CampaignRepository campaignRepository;
    @Mock SlotRepository slotRepository;
    @Mock TransactionTemplate transactionTemplate;

    private CampaignAvailabilityServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> invocation
                .<org.springframework.transaction.support.TransactionCallback<CampaignAvailability>>getArgument(0)
                .doInTransaction(mock(TransactionStatus.class)));
        service = new CampaignAvailabilityServiceImpl(
                cache, campaignRepository, slotRepository, transactionTemplate);
    }

    @Test
    void returnsTheCachedSnapshotWithoutReadingMySql() {
        CampaignAvailability cached = new CampaignAvailability(
                CAMPAIGN_ID, CampaignStatus.ACTIVE, true);
        when(cache.get(CAMPAIGN_ID)).thenReturn(Optional.of(cached));

        CampaignAvailability result = service.get(CAMPAIGN_ID);

        assertThat(result).isSameAs(cached);
        verifyNoInteractions(campaignRepository, slotRepository);
        verify(transactionTemplate, never()).execute(any());
    }

    @Test
    void activeCampaignWithInventoryIsClaimable() {
        when(cache.get(CAMPAIGN_ID)).thenReturn(Optional.empty());
        when(campaignRepository.findById(CAMPAIGN_ID))
                .thenReturn(Optional.of(campaign(CampaignStatus.ACTIVE)));
        when(slotRepository.existsByIdCampaignId(CAMPAIGN_ID)).thenReturn(true);

        CampaignAvailability result = service.get(CAMPAIGN_ID);

        assertThat(result.getStatus()).isEqualTo(CampaignStatus.ACTIVE);
        assertThat(result.isClaimable()).isTrue();
        verify(cache).put(result);
    }

    @Test
    void emptyActiveCampaignBecomesSoldOut() {
        when(cache.get(CAMPAIGN_ID)).thenReturn(Optional.empty());
        when(campaignRepository.findById(CAMPAIGN_ID))
                .thenReturn(Optional.of(campaign(CampaignStatus.ACTIVE)));
        when(slotRepository.existsByIdCampaignId(CAMPAIGN_ID)).thenReturn(false);

        CampaignAvailability result = service.get(CAMPAIGN_ID);

        assertThat(result.getStatus()).isEqualTo(CampaignStatus.SOLD_OUT);
        assertThat(result.isClaimable()).isFalse();
        verify(campaignRepository).markSoldOut(eq(CAMPAIGN_ID), any(Instant.class));
        verify(cache).put(result);
    }

    private VoucherCampaign campaign(CampaignStatus status) {
        Instant now = Instant.now();
        return new VoucherCampaign(
                CAMPAIGN_ID, "1000000000000001", "Campaign", "PERCENTAGE", BigDecimal.TEN,
                100, 0, 100, "v1", "create-idem", status,
                now.minusSeconds(60), now.plusSeconds(3600), now.plusSeconds(7200));
    }
}
