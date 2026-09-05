package com.example.voucherclaim.scheduler;

import com.example.voucherclaim.service.CampaignExpirationCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CampaignExpirationCleanupWatcher {
    private static final Logger log = LoggerFactory.getLogger(CampaignExpirationCleanupWatcher.class);
    private final CampaignExpirationCleanupService cleanupService;

    public CampaignExpirationCleanupWatcher(CampaignExpirationCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    /** Wakes durable cleanup work; campaign state and remaining slot rows survive restarts. */
    @Scheduled(fixedDelayString = "${app.expiration-cleanup.poll-interval:1000}")
    public void cleanupExpiredCampaigns() {
        log.trace("Scanning for expired campaign slots");
        cleanupService.processExpiredCampaigns();
    }
}
