package com.example.voucherclaim.model;

import com.example.voucherclaim.domain.RequestIds;

import java.util.Objects;

public class PriorityRequest {
    private final String requestId;
    private final String campaignId;
    private final String userId;
    private final long scoreSnapshot;

    public PriorityRequest(
            String requestId,
            String campaignId,
            String userId,
            long scoreSnapshot
    ) {
        this.requestId = requestId;
        this.campaignId = campaignId;
        this.userId = userId;
        this.scoreSnapshot = scoreSnapshot;
    }


    public String member() {
        return userId;
    }

    public static PriorityRequest fromMember(String campaignId, String member, double score) {
        if (member == null || member.isBlank()) {
            throw new IllegalArgumentException("Invalid priority queue member");
        }
        String requestId = RequestIds.forClaim(campaignId, member);
        return new PriorityRequest(requestId, campaignId, member, Math.round(score));
    }

    public String getRequestId() { return requestId; }
    public String getCampaignId() { return campaignId; }
    public String getUserId() { return userId; }
    public long getScoreSnapshot() { return scoreSnapshot; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PriorityRequest that)) return false;
        return scoreSnapshot == that.scoreSnapshot
                && Objects.equals(requestId, that.requestId)
                && Objects.equals(campaignId, that.campaignId)
                && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId, campaignId, userId, scoreSnapshot);
    }
}
