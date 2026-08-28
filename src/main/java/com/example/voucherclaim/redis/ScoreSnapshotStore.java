package com.example.voucherclaim.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.OptionalLong;

@Component
public class ScoreSnapshotStore {
    private static final Duration SNAPSHOT_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;

    public ScoreSnapshotStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public OptionalLong get(String campaignId, String userId) {
        String value = redis.opsForValue().get(key(campaignId, userId));
        if (value == null) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Long.parseLong(value));
    }

    public void put(String campaignId, String userId, long score) {
        if (score < 0 || score > 1_000_000_000L) {
            throw new IllegalArgumentException("score must be between 0 and 1,000,000,000");
        }
        redis.opsForValue().set(key(campaignId, userId), Long.toString(score), SNAPSHOT_TTL);
    }

    private String key(String campaignId, String userId) {
        return "claim:score:{" + campaignId + "}:" + userId;
    }
}
