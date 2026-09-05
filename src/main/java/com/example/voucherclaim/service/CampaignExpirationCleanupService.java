package com.example.voucherclaim.service;

/** Application boundary for closing expired campaigns and reclaiming unused slot rows. */
public interface CampaignExpirationCleanupService {
    /** Processes a bounded set of expired or partially cleaned campaigns. */
    void processExpiredCampaigns();
}
