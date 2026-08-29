package com.example.voucherclaim.redis;

import com.example.voucherclaim.config.AppProperties;
import com.example.voucherclaim.entity.VoucherClaim;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Optional cache-aside accelerator for a committed claim result. */
@Component
public class ClaimResultCache {
    private final StringRedisTemplate redis;
    private final RedisJson json;
    private final AppProperties properties;

    public ClaimResultCache(StringRedisTemplate redis, RedisJson json, AppProperties properties) {
        this.redis = redis;
        this.json = json;
        this.properties = properties;
    }

    /** Returns only a positive committed result; a miss never proves that an operation is new. */
    public Optional<VoucherClaim> get(String campaignId, String userId) {
        String value = redis.opsForValue().get(key(campaignId, userId));
        return value == null ? Optional.empty() : Optional.of(json.read(value, VoucherClaim.class));
    }

    /** Publishes the claim only after its MySQL transaction has committed. */
    public void put(VoucherClaim claim) {
        redis.opsForValue().set(
                key(claim.getCampaignId(), claim.getUserId()),
                json.write(claim),
                properties.getResultCache().getTtl()
        );
    }

    private String key(String campaignId, String userId) {
        return "claim:result:{" + campaignId + "}:" + userId;
    }
}
