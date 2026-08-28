package com.example.voucherclaim.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdsTest {

    @Test
    void sameOperationProducesSameRequestId() {
        String campaignId = "019c6fa6-5e22-7abc-9123-abcdef123456";
        String userId = "2000000000000001";

        String first = RequestIds.forClaim(campaignId, userId, "idem-123");
        String second = RequestIds.forClaim(campaignId, userId, "idem-123");

        assertThat(first).isEqualTo(second).hasSize(64);
    }

    @Test
    void differentIdempotencyKeyProducesDifferentRequestId() {
        String campaignId = "019c6fa6-5e22-7abc-9123-abcdef123456";
        String userId = "2000000000000001";

        assertThat(RequestIds.forClaim(campaignId, userId, "idem-a"))
                .isNotEqualTo(RequestIds.forClaim(campaignId, userId, "idem-b"));
    }
}
