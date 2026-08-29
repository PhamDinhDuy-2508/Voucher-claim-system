package com.example.voucherclaim.service.impl;

import com.example.voucherclaim.domain.type.CampaignStatus;
import com.example.voucherclaim.domain.type.ClaimStatus;
import com.example.voucherclaim.domain.type.ProcessingResultType;
import com.example.voucherclaim.domain.RequestIds;
import com.example.voucherclaim.entity.VoucherCampaign;
import com.example.voucherclaim.entity.VoucherClaim;
import com.example.voucherclaim.entity.VoucherClaimSlot;
import com.example.voucherclaim.entity.VoucherClaimSlotId;
import com.example.voucherclaim.model.PriorityRequest;
import com.example.voucherclaim.model.ProcessingResult;
import com.example.voucherclaim.repository.CampaignRepository;
import com.example.voucherclaim.repository.ClaimRepository;
import com.example.voucherclaim.repository.OutboxRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimTransactionServiceImplTest {
    @Mock CampaignRepository campaignRepository;
    @Mock ClaimRepository claimRepository;
    @Mock SlotRepository slotRepository;
    @Mock OutboxRepository outboxRepository;
    @Mock TransactionTemplate transactionTemplate;

    ClaimTransactionServiceImpl service;

    @BeforeEach
    void setUp() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> invocation
                .<org.springframework.transaction.support.TransactionCallback<ProcessingResult>>getArgument(0)
                .doInTransaction(mock(TransactionStatus.class)));
        service = new ClaimTransactionServiceImpl(
                campaignRepository, claimRepository, slotRepository, outboxRepository, transactionTemplate);
    }

    @Test
    void existingNaturalClaimReplaysWithoutTakingAnotherSlot() {
        PriorityRequest request = request();
        VoucherClaim existing = claim(request, UUID.randomUUID());
        when(claimRepository.findByCampaignIdAndUserId(request.getCampaignId(), request.getUserId()))
                .thenReturn(Optional.of(existing));

        ProcessingResult result = service.execute(request);

        assertThat(result.getType()).isEqualTo(ProcessingResultType.REPLAYED);
        assertThat(result.getClaim()).isEqualTo(existing);
        verify(slotRepository, never()).lockOneAvailableSlot(any());
    }

    @Test
    void successfulClaimConsumesOneSlotAndWritesClaimAndOutbox() {
        PriorityRequest request = request();
        when(claimRepository.findByCampaignIdAndUserId(request.getCampaignId(), request.getUserId()))
                .thenReturn(Optional.empty());
        when(campaignRepository.findById(request.getCampaignId()))
                .thenReturn(Optional.of(activeCampaign(request.getCampaignId())));
        VoucherClaimSlot slot = slot(request.getCampaignId(), 7);
        when(slotRepository.lockOneAvailableSlot(request.getCampaignId()))
                .thenReturn(Optional.of(slot));

        ProcessingResult result = service.execute(request);

        assertThat(result.getType()).isEqualTo(ProcessingResultType.CREATED);
        assertThat(result.getClaim().getStatus()).isEqualTo(ClaimStatus.ISSUED);
        assertThat(result.getClaim().getPriorityScoreSnapshot()).isEqualTo(900);
        verify(slotRepository).delete(slot);
        verify(claimRepository).saveAndFlush(result.getClaim());
        verify(outboxRepository).save(any());
    }

    @Test
    void noUnlockedSlotButAnAvailableRowExistsIsBusyNotSoldOut() {
        PriorityRequest request = request();
        when(claimRepository.findByCampaignIdAndUserId(request.getCampaignId(), request.getUserId()))
                .thenReturn(Optional.empty());
        when(campaignRepository.findById(request.getCampaignId()))
                .thenReturn(Optional.of(activeCampaign(request.getCampaignId())));
        when(slotRepository.lockOneAvailableSlot(request.getCampaignId()))
                .thenReturn(Optional.empty());
        when(slotRepository.existsByIdCampaignId(request.getCampaignId())).thenReturn(true);

        ProcessingResult result = service.execute(request);

        assertThat(result.getType()).isEqualTo(ProcessingResultType.BUSY);
        verify(campaignRepository, never()).markSoldOut(any(), any());
    }

    @Test
    void noRemainingSlotMarksCampaignSoldOut() {
        PriorityRequest request = request();
        when(claimRepository.findByCampaignIdAndUserId(request.getCampaignId(), request.getUserId()))
                .thenReturn(Optional.empty());
        when(campaignRepository.findById(request.getCampaignId()))
                .thenReturn(Optional.of(activeCampaign(request.getCampaignId())));
        when(slotRepository.lockOneAvailableSlot(request.getCampaignId()))
                .thenReturn(Optional.empty());
        when(slotRepository.existsByIdCampaignId(request.getCampaignId())).thenReturn(false);

        ProcessingResult result = service.execute(request);

        assertThat(result.getType()).isEqualTo(ProcessingResultType.SOLD_OUT);
        verify(campaignRepository).markSoldOut(eq(request.getCampaignId()), any(Instant.class));
    }

    @Test
    void soldOutCampaignReturnsTheStableSoldOutResult() {
        PriorityRequest request = request();
        when(claimRepository.findByCampaignIdAndUserId(request.getCampaignId(), request.getUserId()))
                .thenReturn(Optional.empty());
        when(campaignRepository.findById(request.getCampaignId()))
                .thenReturn(Optional.of(campaign(request.getCampaignId(), CampaignStatus.SOLD_OUT)));

        ProcessingResult result = service.execute(request);

        assertThat(result.getType()).isEqualTo(ProcessingResultType.SOLD_OUT);
        verify(slotRepository, never()).lockOneAvailableSlot(any());
    }

    private PriorityRequest request() {
        String campaignId = "019c6fa6-5e22-7abc-9123-abcdef123456";
        String userId = "2000000000000001";
        return new PriorityRequest(
                RequestIds.forClaim(campaignId, userId), campaignId, userId, 900);
    }

    private VoucherClaim claim(PriorityRequest request, UUID claimId) {
        return new VoucherClaim(
                claimId, request.getCampaignId(), request.getUserId(), "VCH-EXISTING",
                ClaimStatus.ISSUED, request.getScoreSnapshot(),
                Instant.now(), Instant.now().plusSeconds(3600));
    }

    private VoucherCampaign activeCampaign(String campaignId) {
        return campaign(campaignId, CampaignStatus.ACTIVE);
    }

    private VoucherCampaign campaign(String campaignId, CampaignStatus status) {
        Instant now = Instant.now();
        return new VoucherCampaign(
                campaignId, "1000000000000001", "Campaign", "PERCENTAGE", BigDecimal.TEN,
                100, 0, 100, "v1", "create-idem", status,
                now.minusSeconds(60), now.plusSeconds(3600), now.plusSeconds(7200));
    }

    private VoucherClaimSlot slot(String campaignId, long slotId) {
        return new VoucherClaimSlot(
                new VoucherClaimSlotId(campaignId, slotId), Instant.now());
    }
}
