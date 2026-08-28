package com.example.voucherclaim.controller;

import com.example.voucherclaim.facade.CampaignFacade;
import com.example.voucherclaim.model.CampaignWriteResult;
import com.example.voucherclaim.model.request.ActivateCampaignRequest;
import com.example.voucherclaim.model.request.CreateCampaignRequest;
import com.example.voucherclaim.model.response.CampaignResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP boundary for merchant campaign creation and activation commands. */
@Validated
@RestController
@RequestMapping("/api/v1/campaigns")
public class CampaignController {
    private static final Logger log = LoggerFactory.getLogger(CampaignController.class);
    private final CampaignFacade campaignFacade;

    public CampaignController(CampaignFacade campaignFacade) {
        this.campaignFacade = campaignFacade;
    }

    /** Creates one DRAFT campaign or replays the merchant-scoped idempotent result. */
    @PostMapping
    public ResponseEntity<CampaignResponse> create(
            @RequestHeader("X-Merchant-Id") @NotBlank String merchantId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody CreateCampaignRequest request
    ) {
        log.info("Create campaign request received merchantId={} name={} quantity={}",
                merchantId, request.getName(), request.getTotalQuantity());
        CampaignWriteResult result = campaignFacade.create(merchantId, idempotencyKey, request);
        log.info("Create campaign request completed merchantId={} campaignId={} replayed={} status={}",
                merchantId, result.getCampaign().getCampaignId(), result.isReplayed(),
                result.getCampaign().getStatus());
        return campaignResponse(result, HttpStatus.CREATED);
    }

    /** Materializes inventory and transitions a merchant-owned DRAFT campaign to ACTIVE. */
    @PostMapping("/activate")
    public ResponseEntity<CampaignResponse> activate(
            @RequestHeader("X-Merchant-Id") @NotBlank String merchantId,
            @Valid @RequestBody ActivateCampaignRequest request
    ) {
        log.info("Activate campaign request received merchantId={} campaignId={}",
                merchantId, request.getCampaignId());
        CampaignWriteResult result = campaignFacade.activate(merchantId, request);
        log.info("Activate campaign request completed merchantId={} campaignId={} replayed={} status={}",
                merchantId, result.getCampaign().getCampaignId(), result.isReplayed(),
                result.getCampaign().getStatus());
        return campaignResponse(result, HttpStatus.OK);
    }

    /** Maps a campaign write result to the correct create/replay HTTP status and headers. */
    private ResponseEntity<CampaignResponse> campaignResponse(
            CampaignWriteResult result,
            HttpStatus createdStatus
    ) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(
                result.isReplayed() ? HttpStatus.OK : createdStatus);
        if (result.isReplayed()) {
            response.header("Idempotent-Replayed", "true");
        }
        log.debug("Campaign response mapped campaignId={} httpStatus={}",
                result.getCampaign().getCampaignId(),
                result.isReplayed() ? HttpStatus.OK : createdStatus);
        return response.body(CampaignResponse.from(result.getCampaign()));
    }
}
