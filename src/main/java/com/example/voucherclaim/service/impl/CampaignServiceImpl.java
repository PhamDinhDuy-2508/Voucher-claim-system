package com.example.voucherclaim.service.impl;

import com.example.voucherclaim.config.AppProperties;
import com.example.voucherclaim.domain.CampaignIds;
import com.example.voucherclaim.domain.type.CampaignStatus;
import com.example.voucherclaim.entity.VoucherCampaign;
import com.example.voucherclaim.model.CampaignWriteResult;
import com.example.voucherclaim.model.CreateCampaignCommand;
import com.example.voucherclaim.repository.CampaignRepository;
import com.example.voucherclaim.repository.VoucherClaimSlotBatchWriter;
import com.example.voucherclaim.service.CampaignService;
import com.example.voucherclaim.exception.ServiceException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Service
public class CampaignServiceImpl implements CampaignService {
    private static final Logger log = LoggerFactory.getLogger(CampaignServiceImpl.class);
    private final CampaignRepository campaignRepository;
    private final VoucherClaimSlotBatchWriter slotBatchWriter;
    private final TransactionTemplate transactionTemplate;
    private final AppProperties properties;
    private final CampaignIds campaignIds;

    public CampaignServiceImpl(
            CampaignRepository campaignRepository,
            VoucherClaimSlotBatchWriter slotBatchWriter,
            TransactionTemplate transactionTemplate,
            AppProperties properties,
            CampaignIds campaignIds
    ) {
        this.campaignRepository = campaignRepository;
        this.slotBatchWriter = slotBatchWriter;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.campaignIds = campaignIds;
    }

    /** Creates a DRAFT campaign with merchant-scoped idempotency. */
    @Override
    public CampaignWriteResult create(CreateCampaignCommand command) {
        validateTimes(command);

        // Read first for the common replay path; the database unique constraint resolves
        // concurrent creates that both miss this check.
        Optional<VoucherCampaign> existing = campaignRepository.findByMerchantIdAndCreationIdempotencyKey(
                command.getMerchantId(), command.getIdempotencyKey());
        if (existing.isPresent()) {
            log.info("Campaign creation replay merchantId={} campaignId={}",
                    command.getMerchantId(), existing.get().getCampaignId());
            return new CampaignWriteResult(existing.get(), true);
        }

        return persistDraft(command, newDraftCampaign(command));
    }

    /** Builds the campaign using the internal priority-window snapshot, never a client value. */
    private VoucherCampaign newDraftCampaign(CreateCampaignCommand command) {
        return new VoucherCampaign(
                campaignIds.nextId(),
                command.getMerchantId(),
                command.getName(),
                command.getDiscountType(),
                command.getDiscountValue(),
                command.getTotalQuantity(),
                command.getTotalQuantity(),
                configuredPriorityWindowMs(),
                command.getPriorityPolicyVersion(),
                command.getIdempotencyKey(),
                CampaignStatus.DRAFT,
                command.getStartAt(),
                command.getEndAt(),
                command.getVoucherExpiresAt()
        );
    }

    /** Persists a draft and resolves a concurrent idempotency-key winner when necessary. */
    private CampaignWriteResult persistDraft(CreateCampaignCommand command, VoucherCampaign campaign) {
        try {
            // saveAndFlush raises the merchant/key uniqueness race inside this try block.
            VoucherCampaign saved = transactionTemplate.execute(
                    status -> campaignRepository.saveAndFlush(campaign));
            log.info("Campaign created merchantId={} campaignId={} quantity={} status={}",
                    command.getMerchantId(), campaign.getCampaignId(),
                    campaign.getTotalQuantity(), campaign.getStatus());
            return new CampaignWriteResult(
                    Objects.requireNonNull(saved, "Campaign transaction returned no result"), false);
        } catch (DataIntegrityViolationException race) {
            // Another request committed the same merchant-scoped idempotency key first.
            VoucherCampaign winner = campaignRepository
                    .findByMerchantIdAndCreationIdempotencyKey(
                            command.getMerchantId(), command.getIdempotencyKey())
                    .orElseThrow(() -> race);
            log.info("Concurrent campaign creation replay merchantId={} campaignId={}",
                    command.getMerchantId(), winner.getCampaignId());
            return new CampaignWriteResult(winner, true);
        }
    }

    /** Activates one merchant-owned campaign inside a serialized database transaction. */
    @Override
    public CampaignWriteResult activate(String merchantId, String campaignId) {
        CampaignWriteResult result = transactionTemplate.execute(
                status -> activateInTransaction(merchantId, campaignId));
        return Objects.requireNonNull(result, "Activation transaction returned no result");
    }

    /** Owns the DRAFT -> ACTIVE state transition while the campaign row is locked. */
    private CampaignWriteResult activateInTransaction(String merchantId, String campaignId) {
        // The row lock serializes concurrent activate commands for the same campaign.
        VoucherCampaign campaign = campaignRepository.lockByIdAndMerchant(campaignId, merchantId)
                .orElseThrow(() -> ServiceException.notFound(
                        "CAMPAIGN_NOT_FOUND", "Campaign does not exist or is not owned by this merchant"));

        if (campaign.getStatus() == CampaignStatus.ACTIVE) {
            log.info("Campaign activation replay merchantId={} campaignId={}", merchantId, campaignId);
            return new CampaignWriteResult(campaign, true);
        }
        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw ServiceException.conflict(
                    "CAMPAIGN_CANNOT_ACTIVATE", "Only a DRAFT campaign can be activated");
        }

        // Inventory is fully materialized before ACTIVE becomes visible to claim workers.
        slotBatchWriter.insertAll(campaignId, campaign.getTotalQuantity());
        campaignRepository.activate(campaignId, Instant.now());
        VoucherCampaign active = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalStateException("Activated campaign disappeared"));
        log.info("Campaign activated merchantId={} campaignId={} slots={}",
                merchantId, campaignId, campaign.getTotalQuantity());
        return new CampaignWriteResult(active, false);
    }

    /** Validates business time ordering before any campaign write occurs. */
    private void validateTimes(CreateCampaignCommand command) {
        if (!command.getStartAt().isBefore(command.getEndAt())) {
            throw ServiceException.conflict("INVALID_CAMPAIGN_WINDOW", "startAt must be before endAt");
        }
        if (command.getVoucherExpiresAt().isBefore(command.getEndAt())) {
            throw ServiceException.conflict(
                    "INVALID_VOUCHER_EXPIRY", "voucherExpiresAt must be on or after endAt");
        }
    }

    /** Converts and validates the internal collection-window configuration for persistence. */
    private int configuredPriorityWindowMs() {
        long windowMs = properties.getPriority().getCollectionWindow().toMillis();
        if (windowMs < 10 || windowMs > 1_000) {
            throw new IllegalStateException("app.priority.collection-window must be between 10ms and 1000ms");
        }
        return Math.toIntExact(windowMs);
    }

}
