package com.example.voucherclaim.service.impl;

import com.example.voucherclaim.domain.RequestIds;
import com.example.voucherclaim.domain.type.ClaimRequestStatus;
import com.example.voucherclaim.domain.type.ProcessingResultType;
import com.example.voucherclaim.domain.type.QueueAdmissionResult;
import com.example.voucherclaim.entity.ClaimRequest;
import com.example.voucherclaim.entity.VoucherClaim;
import com.example.voucherclaim.exception.ServiceException;
import com.example.voucherclaim.model.ClaimOperationResult;
import com.example.voucherclaim.model.PriorityRequest;
import com.example.voucherclaim.redis.ClaimResultCache;
import com.example.voucherclaim.repository.ClaimRepository;
import com.example.voucherclaim.service.ClaimRequestQueueService;
import com.example.voucherclaim.service.ClaimRequestService;
import com.example.voucherclaim.service.ClaimService;
import com.example.voucherclaim.service.ScoreSnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ClaimServiceImpl implements ClaimService {
    private static final Logger log = LoggerFactory.getLogger(ClaimServiceImpl.class);

    private final ClaimResultCache claimResultCache;
    private final ScoreSnapshotService scoreSnapshots;
    private final ClaimRequestService claimRequestService;
    private final ClaimRequestQueueService claimRequestQueueService;
    private final ClaimRepository claimRepository;

    public ClaimServiceImpl(
            ClaimResultCache claimResultCache,
            ScoreSnapshotService scoreSnapshots,
            ClaimRequestService claimRequestService,
            ClaimRequestQueueService claimRequestQueueService,
            ClaimRepository claimRepository
    ) {
        this.claimResultCache = claimResultCache;
        this.scoreSnapshots = scoreSnapshots;
        this.claimRequestService = claimRequestService;
        this.claimRequestQueueService = claimRequestQueueService;
        this.claimRepository = claimRepository;
    }

    /**
     * Resolves one campaign-and-user operation. A cache miss checks claim_request first;
     * voucher_claim is never queried merely to prove that a brand-new operation is absent.
     */
    @Override
    public ClaimOperationResult claim(String campaignId, String userId) {
        String requestId = RequestIds.forClaim(campaignId, userId);
        log.debug("Claim received requestId={} campaignId={} userId={}", requestId, campaignId, userId);

        Optional<VoucherClaim> cached = readCommittedCache(campaignId, userId);
        if (cached.isPresent()) {
            log.debug("Claim replay resolved requestId={} source=cache", requestId);
            return new ClaimOperationResult(
                    requestId, campaignId, userId, ClaimRequestStatus.SUCCEEDED,
                    ProcessingResultType.REPLAYED, cached.get(), null);
        }

        // Every claim originates from a durable request. If this row is absent, querying
        // voucher_claim would be redundant under that invariant.
        Optional<ClaimRequest> durableRequest = claimRequestService.find(requestId);
        if (durableRequest.isPresent()) {
            log.debug("Claim request found in MySQL requestId={} campaignId={} userId={} status={}",
                    requestId, campaignId, userId, durableRequest.get().getStatus());
            return resumeExisting(durableRequest.get());
        }

        log.debug("Claim request not found; creating durable admission requestId={} campaignId={} userId={}",
                requestId, campaignId, userId);
        PriorityRequest request = buildPriorityRequest(campaignId, userId, requestId);
        ClaimRequest admitted = claimRequestService.submit(request);
        log.debug("Claim request committed to MySQL requestId={} campaignId={} userId={} status={} score={}",
                requestId, campaignId, userId, admitted.getStatus(), request.getScoreSnapshot());
        return resumeExisting(admitted);
    }

    /** Reuses a terminal result or attaches the HTTP call to the existing durable operation. */
    private ClaimOperationResult resumeExisting(ClaimRequest request) {
        if (request.isTerminal()) {
            ClaimOperationResult terminal = toOperationResult(request, request.getStatus());
            log.debug("Claim request is terminal; Redis priority enqueue skipped requestId={} result={}",
                    request.getRequestId(), terminal.getResultType());
            if (terminal.getClaim() != null) {
                warmClaimCache(terminal.getClaim());
            }
            return terminal;
        }

        QueueAdmissionResult admission = materializeBestEffort(request.getRequestId());
        ClaimRequestStatus responseStatus = admission == QueueAdmissionResult.ADDED
                || admission == QueueAdmissionResult.ALREADY_PENDING
                ? ClaimRequestStatus.QUEUED
                : request.getStatus();
        log.debug("Claim accepted asynchronously requestId={} durableStatus={} responseStatus={} queueResult={}",
                request.getRequestId(), request.getStatus(), responseStatus, admission);
        return toOperationResult(request, responseStatus);
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
    private QueueAdmissionResult materializeBestEffort(String requestId) {
        try {
            log.debug("Sending durable claim request to Redis priority materializer requestId={}", requestId);
            QueueAdmissionResult result = claimRequestQueueService.materialize(requestId);
            log.debug("Redis priority materialization call completed requestId={} result={}", requestId, result);
            return result;
        } catch (RuntimeException materializationFailure) {
            log.warn("Direct priority materialization failed; recovery will retry requestId={}",
                    requestId, materializationFailure);
            return QueueAdmissionResult.SKIPPED;
        }
    }

    /** Builds an API operation snapshot and resolves a terminal claim through claim_request.claim_id. */
    private ClaimOperationResult toOperationResult(
            ClaimRequest request,
            ClaimRequestStatus responseStatus
    ) {
        VoucherClaim claim = null;
        if (request.getClaimId() != null) {
            claim = claimRepository.findById(request.getClaimId()).orElse(null);
        }
        return new ClaimOperationResult(
                request.getRequestId(), request.getCampaignId(), request.getUserId(),
                responseStatus, request.getResultType(), claim, request.getResultMessage());
    }

    private void warmClaimCache(VoucherClaim claim) {
        try {
            claimResultCache.put(claim);
        } catch (RuntimeException cacheFailure) {
            log.warn("Claim result cache write failed claimId={}", claim.getClaimId(), cacheFailure);
        }
    }

    /** Reads durable asynchronous state without polling Redis or mutating queue membership. */
    @Override
    public Optional<ClaimOperationResult> getOperation(String requestId) {
        return claimRequestService.find(requestId)
                .map(request -> toOperationResult(request, request.getStatus()));
    }

    /** Read-side use case kept behind the service boundary. */
    @Override
    public Optional<VoucherClaim> getClaim(String campaignId, String userId) {
        return claimRepository.findByCampaignIdAndUserId(campaignId, userId);
    }
}
