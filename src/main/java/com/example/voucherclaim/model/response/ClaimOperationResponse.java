package com.example.voucherclaim.model.response;

import com.example.voucherclaim.model.ClaimOperationResult;

/** Public representation of one durable asynchronous claim operation. */
public class ClaimOperationResponse {
    private final String requestId;
    private final String campaignId;
    private final String userId;
    private final String status;
    private final String result;
    private final String message;
    private final ClaimResponse claim;

    public ClaimOperationResponse(
            String requestId,
            String campaignId,
            String userId,
            String status,
            String result,
            String message,
            ClaimResponse claim
    ) {
        this.requestId = requestId;
        this.campaignId = campaignId;
        this.userId = userId;
        this.status = status;
        this.result = result;
        this.message = message;
        this.claim = claim;
    }

    public static ClaimOperationResponse from(ClaimOperationResult operation) {
        return new ClaimOperationResponse(
                operation.getRequestId(),
                operation.getCampaignId(),
                operation.getUserId(),
                operation.getStatus().name(),
                operation.getResultType() == null ? null : operation.getResultType().name(),
                operation.getMessage(),
                operation.getClaim() == null ? null : ClaimResponse.from(operation.getClaim())
        );
    }

    public String getRequestId() { return requestId; }
    public String getCampaignId() { return campaignId; }
    public String getUserId() { return userId; }
    public String getStatus() { return status; }
    public String getResult() { return result; }
    public String getMessage() { return message; }
    public ClaimResponse getClaim() { return claim; }
}
