package com.example.voucherclaim.service.impl;

import com.example.voucherclaim.config.AppProperties;
import com.example.voucherclaim.domain.type.CampaignActivationJobStatus;
import com.example.voucherclaim.entity.CampaignActivationJob;
import com.example.voucherclaim.repository.CampaignActivationJobRepository;
import com.example.voucherclaim.repository.CampaignRepository;
import com.example.voucherclaim.repository.VoucherClaimSlotBatchWriter;
import com.example.voucherclaim.service.CampaignActivationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CampaignActivationServiceImpl implements CampaignActivationService {
    private static final Logger log = LoggerFactory.getLogger(CampaignActivationServiceImpl.class);
    private static final List<CampaignActivationJobStatus> READY_STATUSES = List.of(
            CampaignActivationJobStatus.PENDING,
            CampaignActivationJobStatus.RETRY_WAIT
    );

    private final CampaignActivationJobRepository jobRepository;
    private final CampaignRepository campaignRepository;
    private final VoucherClaimSlotBatchWriter slotBatchWriter;
    private final TransactionTemplate transactionTemplate;
    private final AppProperties properties;
    private final String workerId = UUID.randomUUID().toString();

    public CampaignActivationServiceImpl(
            CampaignActivationJobRepository jobRepository,
            CampaignRepository campaignRepository,
            VoucherClaimSlotBatchWriter slotBatchWriter,
            TransactionTemplate transactionTemplate,
            AppProperties properties
    ) {
        this.jobRepository = jobRepository;
        this.campaignRepository = campaignRepository;
        this.slotBatchWriter = slotBatchWriter;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
    }

    /** Limits each scheduler pass so activation work cannot monopolize the scheduler thread. */
    @Override
    public void processDueJobs() {
        Instant now = Instant.now();
        List<String> campaignIds = jobRepository.findEligibleCampaignIds(
                READY_STATUSES,
                CampaignActivationJobStatus.PROCESSING,
                now,
                PageRequest.of(0, properties.getActivation().getRecoveryBatchSize())
        );
        for (String campaignId : campaignIds) {
            processOne(campaignId);
        }
    }

    /** Acquires a durable lease before starting a bounded inventory transaction. */
    private void processOne(String campaignId) {
        boolean acquired = Boolean.TRUE.equals(transactionTemplate.execute(
                status -> acquireLease(campaignId)));
        if (!acquired) {
            return;
        }

        try {
            transactionTemplate.executeWithoutResult(status -> materializeBatch(campaignId));
        } catch (RuntimeException batchFailure) {
            log.warn("Activation batch failed campaignId={} workerId={}",
                    campaignId, workerId, batchFailure);
            releaseForRetry(campaignId);
        }
    }

    /** Serializes lease acquisition and recovers a job whose previous worker disappeared. */
    private boolean acquireLease(String campaignId) {
        Instant now = Instant.now();
        CampaignActivationJob job = jobRepository.lockByCampaignId(campaignId).orElse(null);
        if (job == null || !job.acquireLease(
                workerId, now, now.plus(properties.getActivation().getLeaseDuration()))) {
            return false;
        }
        jobRepository.save(job);
        log.debug("Activation lease acquired campaignId={} workerId={} nextSlotId={}",
                campaignId, workerId, job.getNextSlotId());
        return true;
    }

    /** Commits one slot range and the durable cursor together; the final batch activates the campaign. */
    private void materializeBatch(String campaignId) {
        CampaignActivationJob job = jobRepository.lockByCampaignId(campaignId)
                .orElseThrow(() -> new IllegalStateException("Activation job disappeared"));
        if (!job.isOwnedBy(workerId)) {
            return;
        }

        long firstSlotId = job.getNextSlotId();
        long lastSlotId = Math.min(
                job.getTotalQuantity(),
                firstSlotId + properties.getActivation().getSlotBatchSize() - 1L
        );

        // Slot IDs are deterministic and the job cursor is committed in this same transaction.
        slotBatchWriter.insertRange(campaignId, firstSlotId, lastSlotId);
        boolean completed = job.completeBatch(workerId, lastSlotId + 1, Instant.now());
        CampaignActivationJob saved = jobRepository.save(job);

        if (completed) {
            int activated = campaignRepository.activate(campaignId, Instant.now());
            if (activated != 1) {
                throw new IllegalStateException("Campaign could not transition from ACTIVATING to ACTIVE");
            }
            log.info("Campaign activation completed campaignId={} totalSlots={} attempts={}",
                    campaignId, saved.getTotalQuantity(), saved.getAttempt());
        } else {
            log.debug("Activation batch committed campaignId={} slots={}..{} nextSlotId={}",
                    campaignId, firstSlotId, lastSlotId, saved.getNextSlotId());
        }
    }

    /** Makes a failed batch visible to a later scheduler pass without losing its cursor. */
    private void releaseForRetry(String campaignId) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                CampaignActivationJob job = jobRepository.lockByCampaignId(campaignId).orElse(null);
                if (job == null || !job.isOwnedBy(workerId)) {
                    return;
                }
                Instant now = Instant.now();
                job.markRetry(workerId, now.plus(properties.getActivation().getRetryDelay()), now);
                jobRepository.save(job);
            });
        } catch (RuntimeException retryFailure) {
            // The committed lease is the final fallback; another worker recovers it after expiry.
            log.warn("Activation retry state could not be persisted campaignId={}",
                    campaignId, retryFailure);
        }
    }
}
