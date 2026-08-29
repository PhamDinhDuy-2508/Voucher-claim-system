package com.example.voucherclaim.model;

import com.example.voucherclaim.domain.type.ClaimRequestStatus;
import com.example.voucherclaim.domain.type.ProcessingResultType;
import com.example.voucherclaim.entity.VoucherClaim;

/** Snapshot returned by asynchronous claim admission and status queries. */
public class ClaimOperationResult {
    private final String requestId;
    private final String campaignId;
    private final String userId;
    private final ClaimRequestStatus status;
    private final ProcessingResultType resultType;
    private final VoucherClaim claim;
    private final String message;

    public ClaimOperationResult(
            String requestId,
            String campaignId,
            String userId,
            ClaimRequestStatus status,
            ProcessingResultType resultType,
            VoucherClaim claim,
            String message
    ) {
        this.requestId = requestId;
        this.campaignId = campaignId;
        this.userId = userId;
        this.status = status;
        this.resultType = resultType;
        this.claim = claim;
        this.message = message;
    }

    public String getRequestId() { return requestId; }
    public String getCampaignId() { return campaignId; }
    public String getUserId() { return userId; }
    public ClaimRequestStatus getStatus() { return status; }
    public ProcessingResultType getResultType() { return resultType; }
    public VoucherClaim getClaim() { return claim; }
    public String getMessage() { return message; }

    public boolean isTerminal() {
        return status == ClaimRequestStatus.SUCCEEDED || status == ClaimRequestStatus.REJECTED;
    }
}
