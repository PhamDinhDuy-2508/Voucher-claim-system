package com.example.voucherclaim.facade;

import com.example.voucherclaim.model.CampaignWriteResult;
import com.example.voucherclaim.model.request.ActivateCampaignRequest;
import com.example.voucherclaim.model.request.CreateCampaignRequest;
import com.example.voucherclaim.model.response.CampaignStatusResponse;

/** API-facing orchestration boundary for campaign commands. */
public interface CampaignFacade {
    /** Creates or replays a merchant-scoped campaign command. */
    CampaignWriteResult create(String merchantId, String idempotencyKey, CreateCampaignRequest request);

    /** Activates a merchant-owned campaign and materializes its inventory. */
    CampaignWriteResult activate(String merchantId, ActivateCampaignRequest request);

    /** Reads campaign availability for the public Claim action. */
    CampaignStatusResponse getStatus(String campaignId);
}
