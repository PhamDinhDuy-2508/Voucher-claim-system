package com.example.voucherclaim.scheduler;

import com.example.voucherclaim.config.AppProperties;
import com.example.voucherclaim.model.PriorityRequest;
import com.example.voucherclaim.repository.CampaignRepository;
import com.example.voucherclaim.redis.PriorityQueueService;
import com.example.voucherclaim.service.ClaimWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "app.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PriorityScheduler {
    private static final Logger log = LoggerFactory.getLogger(PriorityScheduler.class);

    private final PriorityQueueService priorityQueue;
    private final CampaignRepository campaignRepository;
    private final ClaimWorker claimWorker;
    private final ThreadPoolTaskExecutor executor;
    private final AppProperties properties;
    private final Map<String, Long> lastDispatchNanos = new ConcurrentHashMap<>();
    private final Map<String, Duration> campaignWindows = new ConcurrentHashMap<>();

    public PriorityScheduler(
            PriorityQueueService priorityQueue,
            CampaignRepository campaignRepository,
            ClaimWorker claimWorker,
            @Qualifier("claimWorkerExecutor") ThreadPoolTaskExecutor executor,
            AppProperties properties
    ) {
        this.priorityQueue = priorityQueue;
        this.campaignRepository = campaignRepository;
        this.claimWorker = claimWorker;
        this.executor = executor;
        this.properties = properties;
    }

    /** Scans only campaigns that Redis reports as having pending queue members. */
    @Scheduled(fixedDelayString = "${app.priority.scheduler-scan-interval:10}")
    public void schedule() {
        long now = System.nanoTime();
        for (String rawCampaignId : priorityQueue.activeCampaignIds()) {
            scheduleCampaign(rawCampaignId, now);
        }
    }

    /** Applies campaign-local window timing before admitting work to the worker pool. */
    private void scheduleCampaign(String rawCampaignId, long now) {
        Optional<String> parsedCampaignId = parseCampaignId(rawCampaignId);
        if (parsedCampaignId.isEmpty()) {
            return;
        }

        String campaignId = parsedCampaignId.get();
        Duration window = campaignWindows.computeIfAbsent(campaignId, this::loadWindow);
        if (isDispatchDue(campaignId, window, now)) {
            dispatch(campaignId);
        }
    }

    /** Rejects malformed Redis set members without aborting the scheduler scan. */
    private Optional<String> parseCampaignId(String rawCampaignId) {
        try {
            UUID.fromString(rawCampaignId);
            return Optional.of(rawCampaignId);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            log.warn("Ignoring invalid active campaign id in Redis: {}", rawCampaignId);
            return Optional.empty();
        }
    }

    /** Returns true after one full collection window has elapsed for the campaign. */
    private boolean isDispatchDue(String campaignId, Duration window, long now) {
        Long previousDispatch = lastDispatchNanos.putIfAbsent(campaignId, now);
        if (previousDispatch == null) {
            // The first observation opens the collection window; no request is popped yet.
            return false;
        }
        if (now - previousDispatch < window.toNanos()) {
            return false;
        }
        lastDispatchNanos.put(campaignId, now);
        return true;
    }

    /** Pops only as many requests as the local worker pool can currently execute. */
    private void dispatch(String campaignId) {
        int batchSize = availableBatchSize();
        if (batchSize == 0) {
            log.debug("Priority dispatch paused campaignId={} reason=no-worker-capacity", campaignId);
            return;
        }

        // ZPOPMAX preserves score ordering among pending members and atomically claims the batch.
        var requests = priorityQueue.popHighest(campaignId, batchSize);
        if (!requests.isEmpty()) {
            log.debug("Priority batch popped campaignId={} requestedBatch={} actualBatch={}",
                    campaignId, batchSize, requests.size());
        }
        for (var request : requests) {
            submit(request);
        }
    }

    /** Calculates the admission gate that shields MySQL from raw HTTP concurrency. */
    private int availableBatchSize() {
        int availableWorkers = Math.max(
                0, properties.getPriority().getMaxWorkerThreads() - executor.getActiveCount());
        return Math.min(properties.getPriority().getAdmissionBatchSize(), availableWorkers);
    }

    /** Re-enqueues a popped request if local executor capacity changed during submission. */
    private void submit(PriorityRequest request) {
        try {
            executor.execute(() -> claimWorker.process(request));
        } catch (TaskRejectedException rejected) {
            log.warn("Worker pool saturated; re-enqueueing request {}", request.getRequestId());
            priorityQueue.enqueue(request);
        }
    }

    /** Loads the immutable scheduling-window snapshot stored with the campaign. */
    private Duration loadWindow(String campaignId) {
        return campaignRepository.findById(campaignId)
                .map(campaign -> Duration.ofMillis(campaign.getPriorityWindowMs()))
                .orElse(properties.getPriority().getCollectionWindow());
    }
}
