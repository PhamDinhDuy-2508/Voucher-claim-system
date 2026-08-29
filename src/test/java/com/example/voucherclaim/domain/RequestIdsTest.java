package com.example.voucherclaim.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdsTest {

    @Test
    void sameOperationProducesSameRequestId() {
        String campaignId = "019c6fa6-5e22-7abc-9123-abcdef123456";
        String userId = "2000000000000001";

        String first = RequestIds.forClaim(campaignId, userId);
        String second = RequestIds.forClaim(campaignId, userId);

        assertThat(first).isEqualTo(second).hasSize(64);
    }

    @Test
    void differentUserProducesDifferentRequestId() {
        String campaignId = "019c6fa6-5e22-7abc-9123-abcdef123456";
        String userId = "2000000000000001";

        assertThat(RequestIds.forClaim(campaignId, userId))
                .isNotEqualTo(RequestIds.forClaim(campaignId, "2000000000000002"));
    }
}
