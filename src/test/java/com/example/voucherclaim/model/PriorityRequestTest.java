package com.example.voucherclaim.model;

import com.example.voucherclaim.domain.RequestIds;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PriorityRequestTest {

    @Test
    void memberRoundTripPreservesLogicalRequest() {
        String campaignId = "019c6fa6-5e22-7abc-9123-abcdef123456";
        String userId = "2000000000000001";
        String key = "idem:key/with-special-characters";
        PriorityRequest original = new PriorityRequest(
                RequestIds.forClaim(campaignId, userId, key), campaignId, userId, key, 900);

        PriorityRequest decoded = PriorityRequest.fromMember(campaignId, original.member(), 900D);

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void retryProducesSameSortedSetMember() {
        String campaignId = "019c6fa6-5e22-7abc-9123-abcdef123456";
        String userId = "2000000000000001";
        String key = "same-key";

        PriorityRequest first = new PriorityRequest("ignored-a", campaignId, userId, key, 100);
        PriorityRequest retry = new PriorityRequest("ignored-b", campaignId, userId, key, 100);

        assertThat(first.member()).isEqualTo(retry.member());
    }
}
