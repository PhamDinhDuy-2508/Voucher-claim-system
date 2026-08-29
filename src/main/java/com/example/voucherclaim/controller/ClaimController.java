package com.example.voucherclaim.controller;

import com.example.voucherclaim.facade.ClaimFacade;
import com.example.voucherclaim.model.ClaimOperationResult;
import com.example.voucherclaim.model.request.CreateClaimRequest;
import com.example.voucherclaim.model.response.ClaimOperationResponse;
import com.example.voucherclaim.model.response.ClaimResponse;
import com.example.voucherclaim.exception.ServiceException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public HTTP boundary for claiming and reading a user's voucher. */
@Validated
@RestController
@RequestMapping("/api/v1/claims")
public class ClaimController {
    private static final Logger log = LoggerFactory.getLogger(ClaimController.class);
    private final ClaimFacade claimFacade;

    public ClaimController(ClaimFacade claimFacade) {
        this.claimFacade = claimFacade;
    }

    /** Submits or replays the one claim identified naturally by campaignId and userId. */
    @PostMapping
    public ResponseEntity<ClaimOperationResponse> claim(
            @Valid @RequestBody CreateClaimRequest request
    ) {
        log.debug("Claim request received campaignId={} userId={}",
                request.getCampaignId(), request.getUserId());
        ClaimOperationResult result = claimFacade.claim(request);
        log.debug("Claim request admitted requestId={} campaignId={} userId={} status={} result={}",
                result.getRequestId(), request.getCampaignId(), request.getUserId(),
                result.getStatus(), result.getResultType());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(
                result.isTerminal() ? HttpStatus.OK : HttpStatus.ACCEPTED);
        if (result.isTerminal()) {
            response.header("Idempotent-Replayed", "true");
        }
        return response.body(ClaimOperationResponse.from(result));
    }

    /** Returns the durable state/result of the asynchronous claim operation. */
    @GetMapping("/status")
    public ClaimOperationResponse getStatus(
            @RequestParam("requestId") @NotBlank String requestId
    ) {
        log.debug("Claim status request received requestId={}", requestId);
        ClaimOperationResult result = claimFacade.getOperation(requestId)
                .orElseThrow(() -> ServiceException.notFound(
                        "CLAIM_REQUEST_NOT_FOUND", "Claim request does not exist"));
        log.debug("Claim status request completed requestId={} status={} result={}",
                requestId, result.getStatus(), result.getResultType());
        return ClaimOperationResponse.from(result);
    }

    /** Reads the durable voucher currently owned by the supplied user in one campaign. */
    @GetMapping("/me")
    public ClaimResponse getMyClaim(
            @RequestHeader("X-User-Id") @NotBlank String userId,
            @RequestParam("campaignId") String campaignId
    ) {
        log.debug("Read claim request received campaignId={} userId={}", campaignId, userId);
        var claim = claimFacade.getClaim(campaignId, userId)
                .orElseThrow(() -> {
                    log.debug("Read claim request not found campaignId={} userId={}", campaignId, userId);
                    return ServiceException.notFound("CLAIM_NOT_FOUND", "Claim does not exist");
                });
        log.debug("Read claim request completed campaignId={} userId={} claimId={}",
                campaignId, userId, claim.getClaimId());
        return ClaimResponse.from(claim);
    }
}
