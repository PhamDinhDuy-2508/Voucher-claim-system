package com.example.voucherclaim.scheduler;

import com.example.voucherclaim.service.CampaignActivationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CampaignActivationWatcher {
    private static final Logger log = LoggerFactory.getLogger(CampaignActivationWatcher.class);
    private final CampaignActivationService activationService;

    public CampaignActivationWatcher(CampaignActivationService activationService) {
        this.activationService = activationService;
    }

    /** Triggers durable DB work; the scheduler itself never owns activation state. */
    @Scheduled(fixedDelayString = "${app.activation.poll-interval:100}")
    public void activateDueCampaigns() {
        log.trace("Scanning for due campaign activation jobs");
        activationService.processDueJobs();
    }
}
