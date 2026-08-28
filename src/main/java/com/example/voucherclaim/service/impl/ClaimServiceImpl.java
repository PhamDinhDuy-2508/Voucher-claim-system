package com.example.voucherclaim.service.impl;

import com.example.voucherclaim.config.AppProperties;
import com.example.voucherclaim.domain.type.ProcessingResultType;
import com.example.voucherclaim.entity.ClaimRequest;
import com.example.voucherclaim.entity.VoucherClaim;
import com.example.voucherclaim.model.PriorityRequest;
import com.example.voucherclaim.model.ProcessingResult;
import com.example.voucherclaim.domain.RequestIds;
import com.example.voucherclaim.redis.IdempotencyResultCache;
import com.example.voucherclaim.redis.RequestResultStore;
import com.example.voucherclaim.repository.ClaimRepository;
import com.example.voucherclaim.service.ClaimService;
import com.example.voucherclaim.service.ClaimRequestQueueService;
import com.example.voucherclaim.service.ClaimRequestService;
import com.example.voucherclaim.service.ScoreSnapshotService;
import com.example.voucherclaim.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;

@Service
public class ClaimServiceImpl implements ClaimService {
    private static final Logger log = LoggerFactory.getLogger(ClaimServiceImpl.class);
    private final IdempotencyResultCache idempotencyCache;
    private final RequestResultStore requestResultStore;
    private final ScoreSnapshotService scoreSnapshots;
    private final ClaimRequestService claimRequestService;
    private final ClaimRequestQueueService claimRequestQueueService;
    private final ClaimRepository claimRepository;
    private final AppProperties properties;

    public ClaimServiceImpl(
            IdempotencyResultCache idempotencyCache,
            RequestResultStore requestResultStore,
            ScoreSnapshotService scoreSnapshots,
            ClaimRequestService claimRequestService,
            ClaimRequestQueueService claimRequestQueueService,
            ClaimRepository claimRepository,
            AppProperties properties
    ) {
        this.idempotencyCache = idempotencyCache;
        this.requestResultStore = requestResultStore;
        this.scoreSnapshots = scoreSnapshots;
        this.claimRequestService = claimRequestService;
        this.claimRequestQueueService = claimRequestQueueService;
        this.claimRepository = claimRepository;
        this.properties = properties;
    }

    /**
     * Orchestrates one synchronous claim attempt without making Redis a correctness boundary.
     * The method returns a replay first, rejects a business duplicate, enqueues one logical
     * request, and waits only for the configured HTTP result deadline.
     */
    @Override
    public ProcessingResult claim(String campaignId, String userId, String idempotencyKey) {
        String requestId = RequestIds.forClaim(campaignId, userId, idempotencyKey);
        log.debug("Claim received requestId={} campaignId={} userId={}", requestId, campaignId, userId);

        // A committed result may be served by Redis or MySQL before any new queue work is created.
        Optional<ProcessingResult> replay = findCommittedReplay(
                campaignId, userId, idempotencyKey, requestId);
        if (replay.isPresent()) {
            log.debug("Claim replay resolved requestId={} source=committed-claim", requestId);
            return replay.get();
        }

        // Idempotency and one-claim-per-user are separate checks: another key is a conflict.
        Optional<ProcessingResult> businessConflict = findBusinessConflict(campaignId, userId, requestId);
        if (businessConflict.isPresent()) {
            log.debug("Claim rejected requestId={} result={}", requestId,
                    ProcessingResultType.ALREADY_CLAIMED);
            return businessConflict.get();
        }

        // A repeated HTTP call can attach to an already durable pending request without
        // requiring the volatile score snapshot to still exist.
        Optional<ClaimRequest> pendingOrTerminal = claimRequestService.find(requestId);
        if (pendingOrTerminal.isPresent()) {
            log.debug("Claim attached to durable request requestId={} status={}", requestId,
                    pendingOrTerminal.get().getStatus());
            Optional<ProcessingResult> terminal = toDurableResult(pendingOrTerminal.get());
            if (terminal.isPresent()) {
                return terminal.get();
            }
            // A retry can repair the fast path immediately; ZADD NX keeps this operation idempotent.
            materializeBestEffort(requestId);
            return awaitWorkerResult(toPriorityRequest(pendingOrTerminal.get()));
        }

        PriorityRequest request = buildPriorityRequest(
                campaignId, userId, idempotencyKey, requestId);
        // MySQL is committed first. Redis is only a derived priority index and is updated after commit.
        ClaimRequest durableRequest = claimRequestService.submit(request);
        log.debug("Claim durably admitted requestId={} status={} score={}", requestId,
                durableRequest.getStatus(), request.getScoreSnapshot());
        Optional<ProcessingResult> terminal = toDurableResult(durableRequest);
        if (terminal.isPresent()) {
            return terminal.get();
        }
        materializeBestEffort(requestId);
        return awaitWorkerResult(request);
    }

    /**
     * Accelerates scheduling by materializing the durable row directly into Redis. A Redis
     * outage must not erase or roll back admission; the Recovery Watcher retries from MySQL.
     */
    private void materializeBestEffort(String requestId) {
        try {
            claimRequestQueueService.materialize(requestId);
        } catch (RuntimeException materializationFailure) {
            log.warn("Direct priority materialization failed; recovery watcher will retry requestId={}",
                    requestId, materializationFailure);
        }
    }


    /**
     * Resolves an exact idempotency replay. Redis is the fast path; MySQL remains authoritative
     * when the cache key expired, was evicted, or was never warmed.
     */
    private Optional<ProcessingResult> findCommittedReplay(
            String campaignId,
            String userId,
            String idempotencyKey,
            String requestId
    ) {
        Optional<VoucherClaim> cached;
        try {
            cached = idempotencyCache.get(campaignId, userId, idempotencyKey);
        } catch (RuntimeException cacheFailure) {
            // Redis is only a replay accelerator; durable idempotency remains available in MySQL.
            log.warn("Idempotency cache read failed; falling back to MySQL", cacheFailure);
            cached = Optional.empty();
        }
        if (cached.isPresent()) {
            return Optional.of(ProcessingResult.replayed(requestId, cached.get()));
        }

        Optional<VoucherClaim> durable = claimRepository.findByCampaignIdAndUserIdAndIdempotencyKey(
                campaignId, userId, idempotencyKey);
        durable.ifPresent(this::warmIdempotencyCache);
        return durable.map(claim -> ProcessingResult.replayed(requestId, claim));
    }

    /** Warms the optional performance cache without allowing a cache failure to hide durable data. */
    private void warmIdempotencyCache(VoucherClaim claim) {
        try {
            idempotencyCache.put(claim);
        } catch (RuntimeException ignored) {
            // The caller already read the committed claim from MySQL, so replay remains safe.
        }
    }

    /** Returns a business conflict when the user already owns a voucher under another key. */
    private Optional<ProcessingResult> findBusinessConflict(
            String campaignId,
            String userId,
            String requestId
    ) {
        return claimRepository.findByCampaignIdAndUserId(campaignId, userId)
                .map(claim -> ProcessingResult.failure(
                        requestId,
                        ProcessingResultType.ALREADY_CLAIMED,
                        "User already claimed this campaign with another operation"
                ));
    }

    /** Builds the immutable queue request from the trusted score snapshot. */
    private PriorityRequest buildPriorityRequest(
            String campaignId,
            String userId,
            String idempotencyKey,
            String requestId
    ) {
        // The same score is used by Redis ordering and persisted on the eventual claim.
        long score = scoreSnapshots.get(campaignId, userId)
                .orElseThrow(() -> ServiceException.busy("Priority score snapshot is unavailable"));
        return new PriorityRequest(requestId, campaignId, userId, idempotencyKey, score);
    }

    private PriorityRequest toPriorityRequest(ClaimRequest request) {
        return new PriorityRequest(request.getRequestId(), request.getCampaignId(), request.getUserId(),
                request.getIdempotencyKey(), request.getPriorityScoreSnapshot());
    }

    /** Waits for the asynchronous worker result for a bounded amount of HTTP request time. */
    private ProcessingResult awaitWorkerResult(PriorityRequest request) {
        Instant deadline = Instant.now().plus(properties.getPriority().getResultWaitTimeout());
        while (Instant.now().isBefore(deadline)) {
            Optional<ProcessingResult> result = requestResultStore.get(
                    request.getCampaignId(), request.getRequestId());
            if (result.isPresent()) {
                log.debug("Claim result observed requestId={} result={}", request.getRequestId(),
                        result.get().getType());
                return result.get();
            }

            // Polling is deliberately bounded; the worker may continue after the HTTP timeout.
            LockSupport.parkNanos(properties.getPriority().getResultPollInterval().toNanos());
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                throw ServiceException.busy("Claim request was interrupted");
            }
        }
        return resolveAfterWaitTimeout(request);
    }

    /** Closes the race in which MySQL committed after the final Redis result poll. */
    private ProcessingResult resolveAfterWaitTimeout(PriorityRequest request) {
        Optional<ProcessingResult> terminal = claimRequestService.find(request.getRequestId())
                .flatMap(this::toDurableResult);
        if (terminal.isPresent()) {
            return terminal.get();
        }
        Optional<VoucherClaim> committed = claimRepository.findByCampaignIdAndUserIdAndIdempotencyKey(
                request.getCampaignId(), request.getUserId(), request.getIdempotencyKey());
        if (committed.isPresent()) {
            return ProcessingResult.replayed(request.getRequestId(), committed.get());
        }
        throw ServiceException.busy("Claim result was not ready before the request timeout");
    }

    /** Reconstructs a terminal result even when the short-lived Redis response is gone. */
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

    /** Read-side use case kept behind the service boundary so controllers never query JPA directly. */
    @Override
    public Optional<VoucherClaim> getClaim(String campaignId, String userId) {
        return claimRepository.findByCampaignIdAndUserId(campaignId, userId);
    }
}
