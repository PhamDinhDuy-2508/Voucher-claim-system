package com.example.voucherclaim.service.impl;

import com.example.voucherclaim.domain.type.CampaignStatus;
import com.example.voucherclaim.entity.VoucherCampaign;
import com.example.voucherclaim.exception.ServiceException;
import com.example.voucherclaim.model.CampaignAvailability;
import com.example.voucherclaim.redis.CampaignAvailabilityCache;
import com.example.voucherclaim.repository.CampaignRepository;
import com.example.voucherclaim.repository.SlotRepository;
import com.example.voucherclaim.service.CampaignAvailabilityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Service
public class CampaignAvailabilityServiceImpl implements CampaignAvailabilityService {
    private static final Logger log = LoggerFactory.getLogger(CampaignAvailabilityServiceImpl.class);

    private final CampaignAvailabilityCache cache;
    private final CampaignRepository campaignRepository;
    private final SlotRepository slotRepository;
    private final TransactionTemplate transactionTemplate;

    public CampaignAvailabilityServiceImpl(
            CampaignAvailabilityCache cache,
            CampaignRepository campaignRepository,
            SlotRepository slotRepository,
            TransactionTemplate transactionTemplate
    ) {
        this.cache = cache;
        this.campaignRepository = campaignRepository;
        this.slotRepository = slotRepository;
        this.transactionTemplate = transactionTemplate;
    }

    /** Uses Redis as a one-second read cache and falls back to an authoritative MySQL check. */
    @Override
    public CampaignAvailability get(String campaignId) {
        Optional<CampaignAvailability> cached = readCache(campaignId);
        if (cached.isPresent()) {
            log.debug("Campaign availability cache hit campaignId={} status={} claimable={}",
                    campaignId, cached.get().getStatus(), cached.get().isClaimable());
            return cached.get();
        }

        CampaignAvailability availability = transactionTemplate.execute(
                status -> loadFromDatabase(campaignId));
        CampaignAvailability resolved = Objects.requireNonNull(
                availability, "Campaign availability transaction returned no result");
        writeCache(resolved);
        log.debug("Campaign availability resolved campaignId={} status={} claimable={}",
                campaignId, resolved.getStatus(), resolved.isClaimable());
        return resolved;
    }

    /** Reads the optional cache without turning a Redis outage into an API failure. */
    private Optional<CampaignAvailability> readCache(String campaignId) {
        try {
            return cache.get(campaignId);
        } catch (RuntimeException cacheFailure) {
            log.warn("Campaign availability cache read failed campaignId={}", campaignId, cacheFailure);
            return Optional.empty();
        }
    }

    /** Derives claimability from lifecycle, time window, and physical inventory. */
    private CampaignAvailability loadFromDatabase(String campaignId) {
        VoucherCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> ServiceException.notFound(
                        "CAMPAIGN_NOT_FOUND", "Campaign does not exist"));
        Instant now = Instant.now();
        boolean withinWindow = !now.isBefore(campaign.getStartAt()) && now.isBefore(campaign.getEndAt());
        boolean claimable = campaign.getStatus() == CampaignStatus.ACTIVE && withinWindow;

        // The indexed existence query detects the last consumed slot without maintaining a hot counter.
        if (claimable && campaign.getUnallocatedQuantity() == 0
                && !slotRepository.existsByIdCampaignId(campaignId)) {
            campaignRepository.markSoldOut(campaignId, now);
            return new CampaignAvailability(campaignId, CampaignStatus.SOLD_OUT, false);
        }
        return new CampaignAvailability(campaignId, campaign.getStatus(), claimable);
    }

    /** Caches the final post-transaction snapshot as a best-effort optimization. */
    private void writeCache(CampaignAvailability availability) {
        try {
            cache.put(availability);
        } catch (RuntimeException cacheFailure) {
            log.warn("Campaign availability cache write failed campaignId={}",
                    availability.getCampaignId(), cacheFailure);
        }
    }
}
