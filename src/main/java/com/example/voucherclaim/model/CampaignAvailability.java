package com.example.voucherclaim.model;

import com.example.voucherclaim.domain.type.CampaignStatus;

/** Snapshot used by the campaign status API and its short-lived Redis cache. */
public class CampaignAvailability {
    private String campaignId;
    private CampaignStatus status;
    private boolean claimable;

    public CampaignAvailability() {
    }

    public CampaignAvailability(String campaignId, CampaignStatus status, boolean claimable) {
        this.campaignId = campaignId;
        this.status = status;
        this.claimable = claimable;
    }

    public String getCampaignId() { return campaignId; }
    public void setCampaignId(String campaignId) { this.campaignId = campaignId; }
    public CampaignStatus getStatus() { return status; }
    public void setStatus(CampaignStatus status) { this.status = status; }
    public boolean isClaimable() { return claimable; }
    public void setClaimable(boolean claimable) { this.claimable = claimable; }
}
