package com.example.voucherclaim.redis;

import com.example.voucherclaim.config.AppProperties;
import com.example.voucherclaim.entity.VoucherClaim;
import com.example.voucherclaim.domain.RequestIds;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class IdempotencyResultCache {
    private final StringRedisTemplate redis;
    private final RedisJson json;
    private final AppProperties properties;

    public IdempotencyResultCache(
            StringRedisTemplate redis,
            RedisJson json,
            AppProperties properties
    ) {
        this.redis = redis;
        this.json = json;
        this.properties = properties;
    }

    public Optional<VoucherClaim> get(String campaignId, String userId, String idempotencyKey) {
        // No negative cache entry is created on miss. MySQL decides whether this is a new
        // operation; Redis only absorbs short-term replay/spam after a successful commit.
        String value = redis.opsForValue().get(key(campaignId, userId, idempotencyKey));
        return value == null ? Optional.empty() : Optional.of(json.read(value, VoucherClaim.class));
    }


    public void put(VoucherClaim claim) {
        redis.opsForValue().set(
                key(claim.getCampaignId(), claim.getUserId(), claim.getIdempotencyKey()),
                json.write(claim),
                properties.getIdempotency().getResultTtl()
        );
    }

    private String key(String campaignId, String userId, String idempotencyKey) {
        return "claim:idem:{" + campaignId + "}:" + RequestIds.forClaim(campaignId, userId, idempotencyKey);
    }
}
