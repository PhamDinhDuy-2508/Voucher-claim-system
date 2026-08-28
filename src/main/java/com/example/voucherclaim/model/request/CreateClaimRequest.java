package com.example.voucherclaim.model.request;

import jakarta.validation.constraints.NotBlank;

public class CreateClaimRequest {
    @NotBlank
    private String userId;

    @NotBlank
    private String campaignId;

    public CreateClaimRequest() {
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getCampaignId() { return campaignId; }
    public void setCampaignId(String campaignId) { this.campaignId = campaignId; }
}
