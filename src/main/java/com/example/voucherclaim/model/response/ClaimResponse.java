package com.example.voucherclaim.model.response;

import com.example.voucherclaim.entity.VoucherClaim;
import java.time.Instant;
import java.util.UUID;

public class ClaimResponse {
    private final UUID claimId;
    private final String campaignId;
    private final String status;
    private final String voucherCode;
    private final long priorityScoreSnapshot;
    private final Instant expiresAt;

    public ClaimResponse(
            UUID claimId,
            String campaignId,
            String status,
            String voucherCode,
            long priorityScoreSnapshot,
            Instant expiresAt
    ) {
        this.claimId = claimId;
        this.campaignId = campaignId;
        this.status = status;
        this.voucherCode = voucherCode;
        this.priorityScoreSnapshot = priorityScoreSnapshot;
        this.expiresAt = expiresAt;
    }

    public static ClaimResponse from(VoucherClaim claim) {
        return new ClaimResponse(
                claim.getClaimId(),
                claim.getCampaignId(),
                claim.getStatus().name(),
                claim.getVoucherCode(),
                claim.getPriorityScoreSnapshot(),
                claim.getExpiresAt()
        );
    }

    public UUID getClaimId() { return claimId; }
    public String getCampaignId() { return campaignId; }
    public String getStatus() { return status; }
    public String getVoucherCode() { return voucherCode; }
    public long getPriorityScoreSnapshot() { return priorityScoreSnapshot; }
    public Instant getExpiresAt() { return expiresAt; }
}
