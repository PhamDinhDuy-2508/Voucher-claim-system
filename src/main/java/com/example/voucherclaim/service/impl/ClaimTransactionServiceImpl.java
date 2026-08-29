package com.example.voucherclaim.service.impl;

import com.example.voucherclaim.domain.type.CampaignStatus;
import com.example.voucherclaim.domain.type.ClaimStatus;
import com.example.voucherclaim.domain.type.OutboxPublishStatus;
import com.example.voucherclaim.domain.type.ProcessingResultType;
import com.example.voucherclaim.entity.OutboxEvent;
import com.example.voucherclaim.entity.VoucherCampaign;
import com.example.voucherclaim.entity.VoucherClaim;
import com.example.voucherclaim.entity.VoucherClaimSlot;
import com.example.voucherclaim.model.PriorityRequest;
import com.example.voucherclaim.model.ProcessingResult;
import com.example.voucherclaim.repository.CampaignRepository;
import com.example.voucherclaim.repository.ClaimRepository;
import com.example.voucherclaim.repository.OutboxRepository;
import com.example.voucherclaim.repository.SlotRepository;
import com.example.voucherclaim.service.ClaimTransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class ClaimTransactionServiceImpl implements ClaimTransactionService {
    private static final Logger log = LoggerFactory.getLogger(ClaimTransactionServiceImpl.class);
    private final CampaignRepository campaignRepository;
    private final ClaimRepository claimRepository;
    private final SlotRepository slotRepository;
    private final OutboxRepository outboxRepository;
    private final TransactionTemplate transactionTemplate;

    public ClaimTransactionServiceImpl(
            CampaignRepository campaignRepository,
            ClaimRepository claimRepository,
            SlotRepository slotRepository,
            OutboxRepository outboxRepository,
            TransactionTemplate transactionTemplate
    ) {
        this.campaignRepository = campaignRepository;
        this.claimRepository = claimRepository;
        this.slotRepository = slotRepository;
        this.outboxRepository = outboxRepository;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Executes the complete correctness-critical claim transaction. Redis admission is
     * intentionally revalidated here because only MySQL constraints and row locks are authoritative.
     */
    @Override
    public ProcessingResult execute(PriorityRequest request) {
        log.debug("Claim transaction started requestId={} campaignId={} userId={}",
                request.getRequestId(), request.getCampaignId(), request.getUserId());
        ProcessingResult result = transactionTemplate.execute(status -> executeInTransaction(request));
        ProcessingResult resolved = Objects.requireNonNull(result, "Claim transaction returned no result");
        log.debug("Claim transaction finished requestId={} result={}",
                request.getRequestId(), resolved.getType());
        return resolved;
    }

    /** Keeps the complete slot/claim/outbox mutation inside one programmatic transaction. */
    private ProcessingResult executeInTransaction(PriorityRequest request) {
        Optional<ProcessingResult> existingResult = resolveExistingClaim(request);
        if (existingResult.isPresent()) {
            return existingResult.get();
        }

        Instant now = Instant.now();
        VoucherCampaign campaign = campaignRepository.findById(request.getCampaignId()).orElse(null);
        if (campaign != null && campaign.getStatus() == CampaignStatus.SOLD_OUT) {
            return soldOut(request);
        }
        if (!isClaimable(campaign, now)) {
            return campaignNotActive(request);
        }

        // SKIP LOCKED chooses another inventory unit instead of waiting on a slot held by a peer.
        Optional<VoucherClaimSlot> slot = slotRepository.lockOneAvailableSlot(request.getCampaignId());
        if (slot.isEmpty()) {
            return resolveUnavailableSlot(request, campaign, now);
        }
        return issueClaim(request, campaign, slot.get(), now);
    }

    /** Rechecks idempotency and one-claim-per-user inside the database transaction. */
    private Optional<ProcessingResult> resolveExistingClaim(PriorityRequest request) {
        Optional<VoucherClaim> existing = claimRepository.findByCampaignIdAndUserId(
                request.getCampaignId(), request.getUserId());
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        VoucherClaim claim = existing.get();
        return Optional.of(ProcessingResult.replayed(request.getRequestId(), claim));
    }

    /** Evaluates both the persisted lifecycle state and the wall-clock claim window. */
    private boolean isClaimable(VoucherCampaign campaign, Instant now) {
        return campaign != null
                && campaign.getStatus() == CampaignStatus.ACTIVE
                && !now.isBefore(campaign.getStartAt())
                && now.isBefore(campaign.getEndAt());
    }

    /** Builds the terminal result for a campaign that cannot currently accept claims. */
    private ProcessingResult campaignNotActive(PriorityRequest request) {
        return ProcessingResult.failure(
                request.getRequestId(),
                ProcessingResultType.CAMPAIGN_NOT_ACTIVE,
                "Campaign is not active"
        );
    }

    /**
     * Distinguishes temporary lock contention from real sold-out inventory. An empty
     * SKIP LOCKED result alone is insufficient because every remaining row may be locked.
     */
    private ProcessingResult resolveUnavailableSlot(
            PriorityRequest request,
            VoucherCampaign campaign,
            Instant now
    ) {
        boolean inventoryStillExists = slotRepository.existsByIdCampaignId(request.getCampaignId())
                || campaign.getUnallocatedQuantity() > 0;
        if (inventoryStillExists) {
            return ProcessingResult.failure(
                    request.getRequestId(),
                    ProcessingResultType.BUSY,
                    "Claim slots are temporarily busy"
            );
        }

        // Marking SOLD_OUT is conditional on ACTIVE, so concurrent workers can safely repeat it.
        campaignRepository.markSoldOut(request.getCampaignId(), now);
        return soldOut(request);
    }

    /** Keeps SOLD_OUT stable for every request processed after inventory exhaustion. */
    private ProcessingResult soldOut(PriorityRequest request) {
        return ProcessingResult.failure(
                request.getRequestId(), ProcessingResultType.SOLD_OUT,
                "Campaign inventory is sold out");
    }

    /** Deletes one locked slot and creates its claim and outbox event in the same transaction. */
    private ProcessingResult issueClaim(
            PriorityRequest request,
            VoucherCampaign campaign,
            VoucherClaimSlot slot,
            Instant now
    ) {
        // A rollback restores the deleted slot and removes both claim and outbox writes.
        slotRepository.delete(slot);
        VoucherClaim claim = newClaim(request, campaign, now);

        // Flush exposes a uniqueness race before a success result can leave this transaction.
        claimRepository.saveAndFlush(claim);
        outboxRepository.save(newClaimedEvent(claim, now));
        log.debug("Voucher issued requestId={} claimId={} campaignId={} slotId={}",
                request.getRequestId(), claim.getClaimId(), request.getCampaignId(),
                slot.getId().getSlotId());
        return ProcessingResult.created(request.getRequestId(), claim);
    }

    /** Creates the durable claim using the exact score snapshot that ordered the queue entry. */
    private VoucherClaim newClaim(PriorityRequest request, VoucherCampaign campaign, Instant now) {
        UUID claimId = UUID.randomUUID();
        return new VoucherClaim(
                claimId,
                request.getCampaignId(),
                request.getUserId(),
                voucherCode(claimId),
                ClaimStatus.ISSUED,
                request.getScoreSnapshot(),
                now,
                campaign.getVoucherExpiresAt()
        );
    }

    /** Creates the integration event that will be published only after this transaction commits. */
    private OutboxEvent newClaimedEvent(VoucherClaim claim, Instant now) {
        return new OutboxEvent(
                UUID.randomUUID(),
                "VoucherClaim",
                claim.getClaimId(),
                "VoucherClaimed",
                Map.of(
                        "claim_id", claim.getClaimId().toString(),
                        "campaign_id", claim.getCampaignId().toString(),
                        "user_id", claim.getUserId().toString(),
                        "status", claim.getStatus().name(),
                        "claimed_at", now.toString()
                ),
                OutboxPublishStatus.PENDING,
                0,
                now
        );
    }

    /** Generates a deterministic-format code from the random claim identifier. */
    private String voucherCode(UUID claimId) {
        return "VCH-" + claimId.toString().replace("-", "").substring(0, 16).toUpperCase();
    }
}
