package com.example.voucherclaim.service.impl;

import com.example.voucherclaim.config.AppProperties;
import com.example.voucherclaim.domain.type.ClaimRequestStatus;
import com.example.voucherclaim.domain.type.ProcessingResultType;
import com.example.voucherclaim.entity.ClaimRequest;
import com.example.voucherclaim.model.PriorityRequest;
import com.example.voucherclaim.model.ProcessingResult;
import com.example.voucherclaim.repository.ClaimRequestRepository;
import com.example.voucherclaim.service.ClaimRequestService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class ClaimRequestServiceImpl implements ClaimRequestService {
    private static final Logger log = LoggerFactory.getLogger(ClaimRequestServiceImpl.class);
    private final ClaimRequestRepository requestRepository;
    private final TransactionTemplate transactionTemplate;
    private final AppProperties properties;

    public ClaimRequestServiceImpl(ClaimRequestRepository requestRepository,
                                   TransactionTemplate transactionTemplate,
                                   AppProperties properties) {
        this.requestRepository = requestRepository;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
    }

    /**
     * Durably admits a request in one short MySQL transaction. Concurrent calls with the same
     * deterministic requestId converge on the row selected by the unique key.
     */
    @Override
    public ClaimRequest submit(PriorityRequest request) {
        try {
            ClaimRequest stored = transactionTemplate.execute(status -> submitInTransaction(request));
            ClaimRequest result = Objects.requireNonNull(stored, "Claim request transaction returned no result");
            log.debug("Durable claim admission resolved requestId={} status={}",
                    result.getRequestId(), result.getStatus());
            return result;
        } catch (DataIntegrityViolationException duplicate) {
            ClaimRequest winner = requestRepository.findById(request.getRequestId())
                    .orElseThrow(() -> duplicate);
            log.debug("Concurrent claim admission replay requestId={} status={}",
                    winner.getRequestId(), winner.getStatus());
            return winner;
        }
    }

    private ClaimRequest submitInTransaction(PriorityRequest request) {
        Optional<ClaimRequest> existing = requestRepository.findById(request.getRequestId());
        if (existing.isPresent()) {
            return existing.get();
        }

        Instant now = Instant.now();
        ClaimRequest claimRequest = new ClaimRequest(
                request.getRequestId(), request.getCampaignId(), request.getUserId(),
                request.getIdempotencyKey(), request.getScoreSnapshot(), maxAttempts(), now);
        requestRepository.saveAndFlush(claimRequest);
        log.debug("Persisted durable claim request requestId={} campaignId={} userId={} score={}",
                request.getRequestId(), request.getCampaignId(), request.getUserId(),
                request.getScoreSnapshot());
        return claimRequest;
    }

    @Override
    public Optional<ClaimRequest> find(String requestId) {
        return requestRepository.findById(requestId);
    }

    /** Returns data for Redis only when the durable row is currently eligible. */
    @Override
    public Optional<PriorityRequest> prepareForEnqueue(String requestId) {
        return transactionTemplate.execute(status -> {
            ClaimRequest request = requestRepository.lockById(requestId).orElse(null);
            if (request == null || request.isTerminal()) {
                return Optional.empty();
            }
            Instant now = Instant.now();
            request.recoverExpiredLease(now);
            if (!request.isReady(now) || request.getAttempt() >= request.getMaxAttempt()) {
                if (request.getAttempt() >= request.getMaxAttempt()) {
                    request.complete(ProcessingResultType.BUSY, null, "Retry budget exhausted", now);
                    requestRepository.save(request);
                    log.warn("Claim retry budget exhausted requestId={} attempts={}",
                            requestId, request.getAttempt());
                }
                return Optional.empty();
            }
            return Optional.of(toPriorityRequest(request));
        });
    }

    @Override
    public void markQueued(String requestId) {
        transactionTemplate.executeWithoutResult(status -> requestRepository.lockById(requestId)
                .ifPresent(request -> {
                    Instant now = Instant.now();
                    request.markQueued(now.plus(properties.getClaimRequest().getQueueRecheckDelay()), now);
                    requestRepository.save(request);
                    log.debug("Claim request marked queued requestId={} nextRecoveryAt={}",
                            requestId, request.getNextAttemptAt());
                }));
    }

    @Override
    public boolean acquireLease(String requestId, String owner) {
        Boolean acquired = transactionTemplate.execute(status -> requestRepository.lockById(requestId)
                .map(request -> {
                    Instant now = Instant.now();
                    boolean result = request.acquireLease(
                            owner, now, now.plus(properties.getClaimRequest().getLeaseDuration()));
                    if (result) requestRepository.save(request);
                    if (result) {
                        log.debug("Claim lease acquired requestId={} owner={} attempt={} leaseUntil={}",
                                requestId, owner, request.getAttempt(), request.getLeaseUntil());
                    } else {
                        log.debug("Claim lease skipped requestId={} status={} currentOwner={}",
                                requestId, request.getStatus(), request.getLeaseOwner());
                    }
                    return result;
                }).orElse(false));
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void complete(String requestId, String leaseOwner, ProcessingResult result) {
        transactionTemplate.executeWithoutResult(status -> requestRepository.lockById(requestId)
                .ifPresent(request -> {
                    UUID claimId = result.getClaim() == null ? null : result.getClaim().getClaimId();
                    if (request.completeIfOwned(leaseOwner, result.getType(), claimId,
                            result.getMessage(), Instant.now())) {
                        requestRepository.save(request);
                        log.debug("Claim request completed requestId={} result={} claimId={}",
                                requestId, result.getType(), claimId);
                    } else {
                        log.debug("Ignored stale completion requestId={} owner={}", requestId, leaseOwner);
                    }
                }));
    }

    @Override
    public void retry(String requestId, String leaseOwner) {
        transactionTemplate.executeWithoutResult(status -> requestRepository.lockById(requestId)
                .ifPresent(request -> {
                    Instant now = Instant.now();
                    boolean changed;
                    if (request.getAttempt() >= request.getMaxAttempt()) {
                        changed = request.completeIfOwned(leaseOwner, ProcessingResultType.BUSY, null,
                                "Retry budget exhausted", now);
                    } else {
                        changed = request.retryIfOwned(leaseOwner,
                                now.plus(properties.getClaimRequest().getRetryDelay()), now);
                    }
                    if (changed) {
                        requestRepository.save(request);
                        log.debug("Claim request retry transition requestId={} status={} attempt={} nextAttemptAt={}",
                                requestId, request.getStatus(), request.getAttempt(), request.getNextAttemptAt());
                    } else {
                        log.debug("Ignored stale retry requestId={} owner={}", requestId, leaseOwner);
                    }
                }));
    }

    @Override
    public List<String> findRecoverableIds() {
        List<String> requestIds = requestRepository.findRecoverableIds(
                List.of(ClaimRequestStatus.PENDING, ClaimRequestStatus.QUEUED, ClaimRequestStatus.RETRY_WAIT),
                ClaimRequestStatus.PROCESSING, Instant.now(),
                PageRequest.of(0, properties.getClaimRequest().getRecoveryBatchSize()));
        if (!requestIds.isEmpty()) {
            log.info("Claim recovery batch selected count={}", requestIds.size());
        }
        return requestIds;
    }

    private PriorityRequest toPriorityRequest(ClaimRequest request) {
        return new PriorityRequest(request.getRequestId(), request.getCampaignId(), request.getUserId(),
                request.getIdempotencyKey(), request.getPriorityScoreSnapshot());
    }

    private int maxAttempts() {
        int value = properties.getClaimRequest().getMaxAttempts();
        if (value < 1) throw new IllegalStateException("app.claim-request.max-attempts must be at least 1");
        return value;
    }
}
