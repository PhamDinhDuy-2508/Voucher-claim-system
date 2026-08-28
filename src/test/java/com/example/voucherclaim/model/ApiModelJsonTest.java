package com.example.voucherclaim.model;

import com.example.voucherclaim.model.request.CreateCampaignRequest;
import com.example.voucherclaim.model.request.CreateClaimRequest;
import com.example.voucherclaim.model.response.CampaignResponse;
import com.example.voucherclaim.model.response.ClaimResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApiModelJsonTest {
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void deserializesCamelCaseCampaignRequest() throws Exception {
        String json = """
                {
                  "name": "Campaign",
                  "discountType": "PERCENTAGE",
                  "discountValue": 10,
                  "totalQuantity": 100,
                  "priorityOrder": "SCORE_DESC_THEN_REQUEST_MEMBER_DESC",
                  "startAt": "2026-09-01T00:00:00Z",
                  "endAt": "2026-09-30T00:00:00Z",
                  "voucherExpiresAt": "2026-10-31T00:00:00Z"
                }
                """;

        CreateCampaignRequest request = objectMapper.readValue(json, CreateCampaignRequest.class);

        assertThat(request.getDiscountType()).isEqualTo("PERCENTAGE");
        assertThat(request.getTotalQuantity()).isEqualTo(100);
        assertThat(request.getVoucherExpiresAt()).isEqualTo(Instant.parse("2026-10-31T00:00:00Z"));
    }

    @Test
    void serializesResponsePropertiesAsCamelCase() throws Exception {
        String campaignId = "019c6fa6-5e22-7abc-9123-abcdef123456";
        JsonNode campaign = objectMapper.readTree(objectMapper.writeValueAsString(new CampaignResponse(
                campaignId,
                "ACTIVE",
                100,
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-30T00:00:00Z")
        )));
        JsonNode claim = objectMapper.readTree(objectMapper.writeValueAsString(new ClaimResponse(
                UUID.randomUUID(),
                campaignId,
                "ISSUED",
                "VCH-001",
                900,
                Instant.parse("2026-10-31T00:00:00Z")
        )));

        assertThat(campaign.has("campaignId")).isTrue();
        assertThat(campaign.has("priorityWindowMs")).isFalse();
        assertThat(campaign.has("campaign_id")).isFalse();
        assertThat(claim.has("claimId")).isTrue();
        assertThat(claim.has("priorityScoreSnapshot")).isTrue();
        assertThat(claim.has("claim_id")).isFalse();
    }

    @Test
    void deserializesClaimUserAndCampaignFromBody() throws Exception {
        String userId = "2000000000000001";
        String campaignId = "019c6fa6-5e22-7abc-9123-abcdef123456";
        String json = "{\"userId\":\"" + userId + "\",\"campaignId\":\"" + campaignId + "\"}";

        CreateClaimRequest request = objectMapper.readValue(json, CreateClaimRequest.class);

        assertThat(request.getUserId()).isEqualTo(userId);
        assertThat(request.getCampaignId()).isEqualTo(campaignId);
    }
}
