package com.example.voucherclaim.model;

import com.example.voucherclaim.domain.RequestIds;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

public class PriorityRequest {
    private final String requestId;
    private final String campaignId;
    private final String userId;
    private final String idempotencyKey;
    private final long scoreSnapshot;

    public PriorityRequest(
            String requestId,
            String campaignId,
            String userId,
            String idempotencyKey,
            long scoreSnapshot
    ) {
        this.requestId = requestId;
        this.campaignId = campaignId;
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        this.scoreSnapshot = scoreSnapshot;
    }


    public String member() {
        String encodedKey = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(idempotencyKey.getBytes(StandardCharsets.UTF_8));
        return userId + ":" + encodedKey;
    }

    public static PriorityRequest fromMember(String campaignId, String member, double score) {
        int separator = member.indexOf(':');
        if (separator < 1 || separator == member.length() - 1) {
            throw new IllegalArgumentException("Invalid priority queue member");
        }
        String userId = member.substring(0, separator);
        String idempotencyKey = new String(
                Base64.getUrlDecoder().decode(member.substring(separator + 1)),
                StandardCharsets.UTF_8
        );
        String requestId = RequestIds.forClaim(campaignId, userId, idempotencyKey);
        return new PriorityRequest(requestId, campaignId, userId, idempotencyKey, Math.round(score));
    }

    public String getRequestId() { return requestId; }
    public String getCampaignId() { return campaignId; }
    public String getUserId() { return userId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public long getScoreSnapshot() { return scoreSnapshot; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PriorityRequest that)) return false;
        return scoreSnapshot == that.scoreSnapshot
                && Objects.equals(requestId, that.requestId)
                && Objects.equals(campaignId, that.campaignId)
                && Objects.equals(userId, that.userId)
                && Objects.equals(idempotencyKey, that.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId, campaignId, userId, idempotencyKey, scoreSnapshot);
    }
}
