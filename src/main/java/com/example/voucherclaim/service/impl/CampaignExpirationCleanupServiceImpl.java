package com.example.voucherclaim.service.impl;

import com.example.voucherclaim.config.AppProperties;
import com.example.voucherclaim.redis.CampaignAvailabilityCache;
import com.example.voucherclaim.repository.CampaignRepository;
import com.example.voucherclaim.repository.SlotRepository;
import com.example.voucherclaim.service.CampaignExpirationCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

@Service
public class CampaignExpirationCleanupServiceImpl implements CampaignExpirationCleanupService {
    private static final Logger log = LoggerFactory.getLogger(CampaignExpirationCleanupServiceImpl.class);

    private final CampaignRepository campaignRepository;
    private final SlotRepository slotRepository;
    private final CampaignAvailabilityCache availabilityCache;
    private final TransactionTemplate transactionTemplate;
    private final AppProperties properties;

    public CampaignExpirationCleanupServiceImpl(
            CampaignRepository campaignRepository,
            SlotRepository slotRepository,
            CampaignAvailabilityCache availabilityCache,
            TransactionTemplate transactionTemplate,
            AppProperties properties
    ) {
        this.campaignRepository = campaignRepository;
        this.slotRepository = slotRepository;
        this.availabilityCache = availabilityCache;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
    }

    /**
     * Campaign status and remaining slot rows form the durable cleanup cursor. An interrupted
     * batch is found again on the next scan, so Redis is never the source of cleanup work.
     */
    @Override
    public void processExpiredCampaigns() {
        Instant now = Instant.now();
        List<String> campaignIds = campaignRepository.findExpirationCleanupCandidates(
                now,
                PageRequest.of(0, properties.getExpirationCleanup().getCampaignBatchSize())
        );

        campaignIds.forEach(campaignId -> cleanupOneBatch(campaignId, now));
    }

    /** Closes admission first, then deletes only one bounded slot batch in the transaction. */
    private void cleanupOneBatch(String campaignId, Instant now) {
        try {
            CleanupResult result = transactionTemplate.execute(status -> {
                int transitioned = campaignRepository.markEndedIfExpired(campaignId, now);
                int deleted = slotRepository.deleteBatchByCampaignId(
                        campaignId,
                        properties.getExpirationCleanup().getSlotDeleteBatchSize()
                );
                return new CleanupResult(transitioned == 1, deleted);
            });

            if (result == null) {
                return;
            }
            if (result.transitioned()) {
                evictAvailabilityBestEffort(campaignId);
                log.info("Campaign expired campaignId={} status=ENDED", campaignId);
            }
            if (result.deletedSlots() > 0) {
                log.info("Expired campaign slots deleted campaignId={} deletedSlots={}",
                        campaignId, result.deletedSlots());
            }
        } catch (RuntimeException failure) {
            // No progress marker is lost: the campaign/slot query discovers this work again.
            log.warn("Expired campaign cleanup batch failed campaignId={}", campaignId, failure);
        }
    }

    /** Cache failure must not roll back an already committed MySQL cleanup batch. */
    private void evictAvailabilityBestEffort(String campaignId) {
        try {
            availabilityCache.evict(campaignId);
        } catch (RuntimeException cacheFailure) {
            log.warn("Campaign availability cache eviction failed campaignId={}",
                    campaignId, cacheFailure);
        }
    }

    private static final class CleanupResult {
        private final boolean transitioned;
        private final int deletedSlots;

        private CleanupResult(boolean transitioned, int deletedSlots) {
            this.transitioned = transitioned;
            this.deletedSlots = deletedSlots;
        }

        private boolean transitioned() {
            return transitioned;
        }

        private int deletedSlots() {
            return deletedSlots;
        }
    }
}
