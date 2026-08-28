package com.example.voucherclaim.service;

import com.example.voucherclaim.model.CampaignWriteResult;
import com.example.voucherclaim.model.CreateCampaignCommand;

/** Business use cases for merchant-owned voucher campaigns. */
public interface CampaignService {
    /** Creates a merchant-owned DRAFT campaign or replays the campaign created by the same key. */
    CampaignWriteResult create(CreateCampaignCommand command);

    /** Materializes inventory slots and transitions one merchant-owned campaign to ACTIVE. */
    CampaignWriteResult activate(String merchantId, String campaignId);
}
