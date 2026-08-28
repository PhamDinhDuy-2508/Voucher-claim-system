package com.example.voucherclaim.model.request;

import jakarta.validation.constraints.NotBlank;

public class ActivateCampaignRequest {
    @NotBlank
    private String campaignId;

    public ActivateCampaignRequest() {
    }

    public String getCampaignId() { return campaignId; }
    public void setCampaignId(String campaignId) { this.campaignId = campaignId; }
}
