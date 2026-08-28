package com.example.voucherclaim.facade;

import com.example.voucherclaim.model.CampaignWriteResult;
import com.example.voucherclaim.model.request.ActivateCampaignRequest;
import com.example.voucherclaim.model.request.CreateCampaignRequest;

/** API-facing orchestration boundary for campaign commands. */
public interface CampaignFacade {
    CampaignWriteResult create(String merchantId, String idempotencyKey, CreateCampaignRequest request);

    CampaignWriteResult activate(String merchantId, ActivateCampaignRequest request);

}
