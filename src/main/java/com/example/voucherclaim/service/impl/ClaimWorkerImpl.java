package com.example.voucherclaim.service.impl;

import com.example.voucherclaim.domain.type.ProcessingResultType;
import com.example.voucherclaim.entity.VoucherClaim;
import com.example.voucherclaim.model.PriorityRequest;
import com.example.voucherclaim.model.ProcessingResult;
import com.example.voucherclaim.redis.ClaimResultCache;
import com.example.voucherclaim.redis.RequestResultStore;
import com.example.voucherclaim.repository.ClaimRepository;
import com.example.voucherclaim.service.ClaimTransactionService;
import com.example.voucherclaim.service.ClaimRequestService;
import com.example.voucherclaim.service.ClaimWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ClaimWorkerImpl implements ClaimWorker {
    private static final Logger log = LoggerFactory.getLogger(ClaimWorkerImpl.class);

    private final ClaimTransactionService transactionService;
    private final ClaimRepository claimRepository;
    private final ClaimResultCache claimResultCache;
    private final RequestResultStore requestResultStore;
    private final ClaimRequestService claimRequestService;
    private final String workerId = UUID.randomUUID().toString();

    public ClaimWorkerImpl(
            ClaimTransactionService transactionService,
            ClaimRepository claimRepository,
            ClaimResultCache claimResultCache,
            RequestResultStore requestResultStore,
            ClaimRequestService claimRequestService
    ) {
        this.transactionService = transactionService;
        this.claimRepository = claimRepository;
        this.claimResultCache = claimResultCache;
        this.requestResultStore = requestResultStore;
        this.claimRequestService = claimRequestService;
    }

    /**
     * Processes one admitted queue request and publishes only post-transaction cache data.
     * Database correctness is delegated to ClaimTransactionService.
     */
    @Override
    public void process(PriorityRequest request) {
        log.debug("Claim worker received requestId={} campaignId={} userId={}",
                request.getRequestId(), request.getCampaignId(), request.getUserId());
        // Redis entries may be duplicated or reconstructed. The MySQL lease picks one owner.
        if (!claimRequestService.acquireLease(request.getRequestId(), workerId)) {
            log.debug("Claim worker skipped requestId={} reason=lease-not-acquired",
                    request.getRequestId());
            return;
        }
        ProcessingResult result = executeSafely(request);
        if (result.getType() == ProcessingResultType.BUSY) {
            claimRequestService.retry(request.getRequestId(), workerId);
            log.debug("Claim worker scheduled retry requestId={}", request.getRequestId());
            return;
        }
        claimRequestService.complete(request.getRequestId(), workerId, result);
        log.debug("Claim worker completed requestId={} result={}",
                request.getRequestId(), result.getType());
        cacheCommittedClaim(request, result);
        publishRequestResult(request, result);
    }

    /** Converts database races and transient failures into stable worker outcomes. */
    private ProcessingResult executeSafely(PriorityRequest request) {
        try {
            return transactionService.execute(request);
        } catch (DataIntegrityViolationException duplicate) {
            // The unique (campaign_id, user_id) constraint chose a winner. Resolve the
            // committed row instead of retrying an inventory write.
            return resolveDuplicate(request);
        } catch (TransientDataAccessException transientFailure) {
            log.warn("Transient database failure for claim request {}", request.getRequestId(), transientFailure);
            return ProcessingResult.failure(
                    request.getRequestId(), ProcessingResultType.BUSY, "Database is temporarily busy");
        } catch (RuntimeException unexpected) {
            log.error("Unexpected claim worker failure for request {}", request.getRequestId(), unexpected);
            return ProcessingResult.failure(
                    request.getRequestId(), ProcessingResultType.BUSY, "Claim processing failed temporarily");
        }
    }

    /** Caches only a committed claim; cache failure cannot roll back or hide the MySQL result. */
    private void cacheCommittedClaim(PriorityRequest request, ProcessingResult result) {
        if (result.getClaim() == null) {
            return;
        }
        try {
            // transactionService has returned, therefore its Spring transaction has committed.
            claimResultCache.put(result.getClaim());
        } catch (RuntimeException cacheFailure) {
            log.warn("Claim committed but result cache write failed for {}", request.getRequestId(), cacheFailure);
        }
    }

    /** Publishes the short-lived response consumed by the waiting HTTP request. */
    private void publishRequestResult(PriorityRequest request, ProcessingResult result) {
        try {
            requestResultStore.put(request.getCampaignId(), result);
        } catch (RuntimeException cacheFailure) {
            log.warn("Cannot publish request result {} to Redis", request.getRequestId(), cacheFailure);
        }
    }

    /** Resolves the durable winner selected by the database uniqueness constraint. */
    private ProcessingResult resolveDuplicate(PriorityRequest request) {
        VoucherClaim winner = claimRepository.findByCampaignIdAndUserId(
                        request.getCampaignId(), request.getUserId())
                .orElse(null);
        if (winner == null) {
            // The concurrent winner may not be visible yet; retrying with the same key is safe.
            return ProcessingResult.failure(
                    request.getRequestId(), ProcessingResultType.BUSY, "Concurrent claim outcome is not visible yet");
        }

        return ProcessingResult.replayed(request.getRequestId(), winner);
    }
}
