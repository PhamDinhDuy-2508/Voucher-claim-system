package com.example.voucherclaim.service;

import com.example.voucherclaim.model.CampaignAvailability;

/** Read use case for campaign availability. */
public interface CampaignAvailabilityService {
    /** Returns the current status used by clients to enable or disable the Claim action. */
    CampaignAvailability get(String campaignId);
}
