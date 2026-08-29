package com.example.voucherclaim.service.impl;

import com.example.voucherclaim.config.AppProperties;
import com.example.voucherclaim.domain.RequestIds;
import com.example.voucherclaim.entity.ClaimRequest;
import com.example.voucherclaim.entity.VoucherClaim;
import com.example.voucherclaim.exception.ServiceException;
import com.example.voucherclaim.model.PriorityRequest;
import com.example.voucherclaim.model.ProcessingResult;
import com.example.voucherclaim.redis.ClaimResultCache;
import com.example.voucherclaim.redis.RequestResultStore;
import com.example.voucherclaim.repository.ClaimRepository;
import com.example.voucherclaim.service.ClaimRequestQueueService;
import com.example.voucherclaim.service.ClaimRequestService;
import com.example.voucherclaim.service.ClaimService;
import com.example.voucherclaim.service.ScoreSnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;

@Service
public class ClaimServiceImpl implements ClaimService {
    private static final Logger log = LoggerFactory.getLogger(ClaimServiceImpl.class);

    private final ClaimResultCache claimResultCache;
    private final RequestResultStore requestResultStore;
    private final ScoreSnapshotService scoreSnapshots;
    private final ClaimRequestService claimRequestService;
    private final ClaimRequestQueueService claimRequestQueueService;
    private final ClaimRepository claimRepository;
    private final AppProperties properties;

    public ClaimServiceImpl(
            ClaimResultCache claimResultCache,
            RequestResultStore requestResultStore,
            ScoreSnapshotService scoreSnapshots,
            ClaimRequestService claimRequestService,
            ClaimRequestQueueService claimRequestQueueService,
            ClaimRepository claimRepository,
            AppProperties properties
    ) {
        this.claimResultCache = claimResultCache;
        this.requestResultStore = requestResultStore;
        this.scoreSnapshots = scoreSnapshots;
        this.claimRequestService = claimRequestService;
        this.claimRequestQueueService = claimRequestQueueService;
        this.claimRepository = claimRepository;
        this.properties = properties;
    }

    /**
     * Resolves one campaign-and-user operation. A cache miss checks claim_request first;
     * voucher_claim is never queried merely to prove that a brand-new operation is absent.
     */
    @Override
    public ProcessingResult claim(String campaignId, String userId) {
        String requestId = RequestIds.forClaim(campaignId, userId);
        log.debug("Claim received requestId={} campaignId={} userId={}", requestId, campaignId, userId);

        Optional<VoucherClaim> cached = readCommittedCache(campaignId, userId);
        if (cached.isPresent()) {
            log.debug("Claim replay resolved requestId={} source=cache", requestId);
            return ProcessingResult.replayed(requestId, cached.get());
        }

        // Every claim originates from a durable request. If this row is absent, querying
        // voucher_claim would be redundant under that invariant.
        Optional<ClaimRequest> durableRequest = claimRequestService.find(requestId);
        if (durableRequest.isPresent()) {
            return resumeExisting(durableRequest.get());
        }

        PriorityRequest request = buildPriorityRequest(campaignId, userId, requestId);
        ClaimRequest admitted = claimRequestService.submit(request);
        log.debug("Claim durably admitted requestId={} status={} score={}",
                requestId, admitted.getStatus(), request.getScoreSnapshot());
        return resumeExisting(admitted);
    }

    /** Reuses a terminal result or attaches the HTTP call to the existing durable operation. */
    private ProcessingResult resumeExisting(ClaimRequest request) {
        Optional<ProcessingResult> terminal = toDurableResult(request);
        if (terminal.isPresent()) {
            if (terminal.get().getClaim() != null) {
                warmClaimCache(terminal.get().getClaim());
            }
            return terminal.get();
        }

        materializeBestEffort(request.getRequestId());
        return awaitWorkerResult(toPriorityRequest(request));
    }

    /** Builds a new queue request only after both cache and durable request lookup miss. */
    private PriorityRequest buildPriorityRequest(String campaignId, String userId, String requestId) {
        long score = scoreSnapshots.get(userId)
                .orElseThrow(() -> ServiceException.busy("Priority score snapshot is unavailable"));
        return new PriorityRequest(requestId, campaignId, userId, score);
    }

    /** Keeps Redis optional; MySQL request state remains the source of truth. */
    private Optional<VoucherClaim> readCommittedCache(String campaignId, String userId) {
        try {
            return claimResultCache.get(campaignId, userId);
        } catch (RuntimeException cacheFailure) {
            log.warn("Claim result cache read failed campaignId={} userId={}",
                    campaignId, userId, cacheFailure);
            return Optional.empty();
        }
    }

    /** Accelerates scheduling after the request transaction has committed. */
    private void materializeBestEffort(String requestId) {
        try {
            claimRequestQueueService.materialize(requestId);
        } catch (RuntimeException materializationFailure) {
            log.warn("Direct priority materialization failed; recovery will retry requestId={}",
                    requestId, materializationFailure);
        }
    }

    private PriorityRequest toPriorityRequest(ClaimRequest request) {
        return new PriorityRequest(request.getRequestId(), request.getCampaignId(), request.getUserId(),
                request.getPriorityScoreSnapshot());
    }

    /** Waits only for the configured HTTP deadline; durable processing may continue afterwards. */
    private ProcessingResult awaitWorkerResult(PriorityRequest request) {
        Instant deadline = Instant.now().plus(properties.getPriority().getResultWaitTimeout());
        while (Instant.now().isBefore(deadline)) {
            Optional<ProcessingResult> result = requestResultStore.get(
                    request.getCampaignId(), request.getRequestId());
            if (result.isPresent()) {
                return result.get();
            }
            LockSupport.parkNanos(properties.getPriority().getResultPollInterval().toNanos());
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                throw ServiceException.busy("Claim request was interrupted");
            }
        }
        return resolveAfterWaitTimeout(request);
    }

    /** Checks voucher_claim only because the durable request is known to exist. */
    private ProcessingResult resolveAfterWaitTimeout(PriorityRequest request) {
        Optional<ClaimRequest> durableRequest = claimRequestService.find(request.getRequestId());
        Optional<ProcessingResult> terminal = durableRequest.flatMap(this::toDurableResult);
        if (terminal.isPresent()) {
            if (terminal.get().getClaim() != null) {
                warmClaimCache(terminal.get().getClaim());
            }
            return terminal.get();
        }

        // A worker may have committed the claim immediately before crashing without completing
        // claim_request. This lookup closes only that existing-request race.
        if (durableRequest.isPresent()) {
            Optional<VoucherClaim> committed = claimRepository.findByCampaignIdAndUserId(
                    request.getCampaignId(), request.getUserId());
            if (committed.isPresent()) {
                warmClaimCache(committed.get());
                return ProcessingResult.replayed(request.getRequestId(), committed.get());
            }
        }
        throw ServiceException.busy("Claim result was not ready before the request timeout");
    }

    /** Reconstructs a terminal result through claim_request.claim_id. */
    private Optional<ProcessingResult> toDurableResult(ClaimRequest request) {
        if (!request.isTerminal() || request.getResultType() == null) {
            return Optional.empty();
        }
        if (request.getClaimId() != null) {
            Optional<VoucherClaim> claim = claimRepository.findById(request.getClaimId());
            if (claim.isPresent()) {
                return Optional.of(new ProcessingResult(request.getRequestId(), request.getResultType(),
                        claim.get(), request.getResultMessage()));
            }
        }
        return Optional.of(ProcessingResult.failure(
                request.getRequestId(), request.getResultType(), request.getResultMessage()));
    }

    private void warmClaimCache(VoucherClaim claim) {
        try {
            claimResultCache.put(claim);
        } catch (RuntimeException cacheFailure) {
            log.warn("Claim result cache write failed claimId={}", claim.getClaimId(), cacheFailure);
        }
    }

    /** Read-side use case kept behind the service boundary. */
    @Override
    public Optional<VoucherClaim> getClaim(String campaignId, String userId) {
        return claimRepository.findByCampaignIdAndUserId(campaignId, userId);
    }
}
