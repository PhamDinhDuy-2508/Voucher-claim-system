package com.example.voucherclaim.model.response;

import com.example.voucherclaim.entity.VoucherCampaign;
import java.time.Instant;

public class CampaignResponse {
    private final String campaignId;
    private final String status;
    private final long totalQuantity;
    private final Instant startAt;
    private final Instant endAt;

    public CampaignResponse(
            String campaignId,
            String status,
            long totalQuantity,
            Instant startAt,
            Instant endAt
    ) {
        this.campaignId = campaignId;
        this.status = status;
        this.totalQuantity = totalQuantity;
        this.startAt = startAt;
        this.endAt = endAt;
    }


    public static CampaignResponse from(VoucherCampaign campaign) {
        return new CampaignResponse(
                campaign.getCampaignId(),
                campaign.getStatus().name(),
                campaign.getTotalQuantity(),
                campaign.getStartAt(),
                campaign.getEndAt()
        );
    }

    public String getCampaignId() { return campaignId; }
    public String getStatus() { return status; }
    public long getTotalQuantity() { return totalQuantity; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
}
