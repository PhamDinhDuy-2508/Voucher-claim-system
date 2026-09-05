package com.example.voucherclaim.redis;

import com.example.voucherclaim.config.AppProperties;
import com.example.voucherclaim.model.CampaignAvailability;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Short-lived read cache for campaign availability. MySQL remains authoritative. */
@Component
public class CampaignAvailabilityCache {
    private final StringRedisTemplate redis;
    private final RedisJson json;
    private final AppProperties properties;

    public CampaignAvailabilityCache(
            StringRedisTemplate redis,
            RedisJson json,
            AppProperties properties
    ) {
        this.redis = redis;
        this.json = json;
        this.properties = properties;
    }

    public Optional<CampaignAvailability> get(String campaignId) {
        String value = redis.opsForValue().get(key(campaignId));
        return value == null
                ? Optional.empty()
                : Optional.of(json.read(value, CampaignAvailability.class));
    }

    public void put(CampaignAvailability availability) {
        redis.opsForValue().set(
                key(availability.getCampaignId()),
                json.write(availability),
                properties.getAvailability().getCacheTtl()
        );
    }

    /** Removes stale availability immediately after a lifecycle transition. */
    public void evict(String campaignId) {
        redis.delete(key(campaignId));
    }

    private String key(String campaignId) {
        return "campaign:availability:{" + campaignId + "}";
    }
}
