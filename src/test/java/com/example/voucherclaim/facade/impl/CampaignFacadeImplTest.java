package com.example.voucherclaim.facade.impl;

import com.example.voucherclaim.model.CreateCampaignCommand;
import com.example.voucherclaim.model.request.CreateCampaignRequest;
import com.example.voucherclaim.service.CampaignService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CampaignFacadeImplTest {
    @Test
    void mapsTheApiRequestToAnInternalCampaignCommand() {
        CampaignService campaignService = mock(CampaignService.class);
        CampaignFacadeImpl facade = new CampaignFacadeImpl(campaignService);
        CreateCampaignRequest request = request();
        String merchantId = "1000000000000001";

        facade.create(merchantId, "create-key", request);

        ArgumentCaptor<CreateCampaignCommand> command = ArgumentCaptor.forClass(CreateCampaignCommand.class);
        verify(campaignService).create(command.capture());
        assertThat(command.getValue().getMerchantId()).isEqualTo(merchantId);
        assertThat(command.getValue().getIdempotencyKey()).isEqualTo("create-key");
        assertThat(command.getValue().getName()).isEqualTo("Priority campaign");
        assertThat(command.getValue().getTotalQuantity()).isEqualTo(5_000);
    }

    private CreateCampaignRequest request() {
        CreateCampaignRequest request = new CreateCampaignRequest();
        request.setName("Priority campaign");
        request.setDiscountType("PERCENTAGE");
        request.setDiscountValue(BigDecimal.TEN);
        request.setTotalQuantity(5_000);
        request.setPriorityOrder("SCORE_DESC_THEN_REQUEST_MEMBER_DESC");
        request.setStartAt(Instant.parse("2026-08-28T00:00:00Z"));
        request.setEndAt(Instant.parse("2026-08-29T00:00:00Z"));
        request.setVoucherExpiresAt(Instant.parse("2026-09-01T00:00:00Z"));
        return request;
    }
}
