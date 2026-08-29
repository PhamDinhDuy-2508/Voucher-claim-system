package com.example.voucherclaim.service;

/** Application boundary for recovering and processing durable activation jobs. */
public interface CampaignActivationService {
    /** Acquires a bounded set of due jobs and materializes one slot batch for each. */
    void processDueJobs();
}
