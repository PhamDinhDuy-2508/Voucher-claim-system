package com.example.voucherclaim.facade.impl;

import com.example.voucherclaim.facade.CampaignFacade;
import com.example.voucherclaim.model.CampaignWriteResult;
import com.example.voucherclaim.model.CreateCampaignCommand;
import com.example.voucherclaim.model.request.ActivateCampaignRequest;
import com.example.voucherclaim.model.request.CreateCampaignRequest;
import com.example.voucherclaim.service.CampaignService;
import org.springframework.stereotype.Component;

@Component
public class CampaignFacadeImpl implements CampaignFacade {
    private final CampaignService campaignService;

    public CampaignFacadeImpl(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    /** Converts the HTTP DTO into the internal command before entering business logic. */
    @Override
    public CampaignWriteResult create(
            String merchantId,
            String idempotencyKey,
            CreateCampaignRequest request
    ) {
        return campaignService.create(new CreateCampaignCommand(
                merchantId,
                idempotencyKey,
                request.getName(),
                request.getDiscountType(),
                request.getDiscountValue(),
                request.getTotalQuantity(),
                request.getPriorityOrder(),
                request.getStartAt(),
                request.getEndAt(),
                request.getVoucherExpiresAt()
        ));
    }

    @Override
    public CampaignWriteResult activate(String merchantId, ActivateCampaignRequest request) {
        return campaignService.activate(merchantId, request.getCampaignId());
    }
}
