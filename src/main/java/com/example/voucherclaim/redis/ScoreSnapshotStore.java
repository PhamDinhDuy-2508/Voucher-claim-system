package com.example.voucherclaim.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.OptionalLong;

@Component
public class ScoreSnapshotStore {
    private static final Duration SNAPSHOT_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redis;

    public ScoreSnapshotStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Reads the global user score cache without coupling it to a campaign. */
    public OptionalLong get(String userId) {
        String value = redis.opsForValue().get(key(userId));
        if (value == null) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Long.parseLong(value));
    }

    /** Replaces the cached score after its durable MySQL transaction commits. */
    public void put(String userId, long score) {
        if (score < 0 || score > 1_000_000_000L) {
            throw new IllegalArgumentException("score must be between 0 and 1,000,000,000");
        }
        redis.opsForValue().set(key(userId), Long.toString(score), SNAPSHOT_TTL);
    }

    private String key(String userId) {
        return "user:score:{" + userId + "}";
    }
}
