package com.example.voucherclaim.controller;

import com.example.voucherclaim.facade.ClaimFacade;
import com.example.voucherclaim.model.ProcessingResult;
import com.example.voucherclaim.model.request.CreateClaimRequest;
import com.example.voucherclaim.model.response.ClaimResponse;
import com.example.voucherclaim.exception.ServiceException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public HTTP boundary for claiming and reading a user's voucher. */
@Validated
@RestController
public class ClaimController {
    private static final Logger log = LoggerFactory.getLogger(ClaimController.class);
    private final ClaimFacade claimFacade;

    public ClaimController(ClaimFacade claimFacade) {
        this.claimFacade = claimFacade;
    }

    /** Submits or replays one idempotent claim without logging the idempotency key itself. */
    @PostMapping("/api/v1/claims")
    public ResponseEntity<ClaimResponse> claim(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody CreateClaimRequest request
    ) {
        log.debug("Claim request received campaignId={} userId={}",
                request.getCampaignId(), request.getUserId());
        ProcessingResult result = claimFacade.claim(idempotencyKey, request);
        log.debug("Claim request resolved requestId={} campaignId={} userId={} result={}",
                result.getRequestId(), request.getCampaignId(), request.getUserId(), result.getType());
        return switch (result.getType()) {
            case CREATED -> ResponseEntity.status(HttpStatus.CREATED).body(ClaimResponse.from(result.getClaim()));
            case REPLAYED -> ResponseEntity.ok()
                    .header("Idempotent-Replayed", "true")
                    .body(ClaimResponse.from(result.getClaim()));
            case ALREADY_CLAIMED -> throw ServiceException.conflict("ALREADY_CLAIMED", result.getMessage());
            case SOLD_OUT -> throw ServiceException.conflict("SOLD_OUT", result.getMessage());
            case BUSY -> throw ServiceException.busy(result.getMessage());
            case CAMPAIGN_NOT_ACTIVE -> throw new ServiceException(
                    HttpStatus.GONE, "CAMPAIGN_NOT_ACTIVE", result.getMessage(), false);
        };
    }

    /** Reads the durable voucher currently owned by the supplied user in one campaign. */
    @GetMapping("/v1/claims/me")
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
