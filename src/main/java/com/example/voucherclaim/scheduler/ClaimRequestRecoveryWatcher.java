package com.example.voucherclaim.scheduler;

import com.example.voucherclaim.service.ClaimRequestQueueService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ClaimRequestRecoveryWatcher {
    private final ClaimRequestQueueService queueService;

    public ClaimRequestRecoveryWatcher(ClaimRequestQueueService queueService) {
        this.queueService = queueService;
    }

    /** MySQL-backed recovery trigger; Redis never owns the only copy of pending work. */
    @Scheduled(fixedDelayString = "${app.claim-request.recovery-interval:2000}")
    public void recover() {
        queueService.recoverDueRequests();
    }
}
