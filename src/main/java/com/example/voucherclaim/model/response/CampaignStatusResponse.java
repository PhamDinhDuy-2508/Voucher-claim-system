package com.example.voucherclaim.model.response;

import com.example.voucherclaim.model.CampaignAvailability;

/** Public campaign availability response used by clients to control the Claim action. */
public class CampaignStatusResponse {
    private final String campaignId;
    private final String status;
    private final boolean claimable;

    public CampaignStatusResponse(String campaignId, String status, boolean claimable) {
        this.campaignId = campaignId;
        this.status = status;
        this.claimable = claimable;
    }

    public static CampaignStatusResponse from(CampaignAvailability availability) {
        return new CampaignStatusResponse(
                availability.getCampaignId(),
                availability.getStatus().name(),
                availability.isClaimable()
        );
    }

    public String getCampaignId() { return campaignId; }
    public String getStatus() { return status; }
    public boolean isClaimable() { return claimable; }
}
