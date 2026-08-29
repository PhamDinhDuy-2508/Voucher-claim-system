package com.example.voucherclaim.redis;

import com.example.voucherclaim.config.AppProperties;
import com.example.voucherclaim.model.ProcessingResult;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class RequestResultStore {
    private final StringRedisTemplate redis;
    private final RedisJson json;
    private final AppProperties properties;

    public RequestResultStore(StringRedisTemplate redis, RedisJson json, AppProperties properties) {
        this.redis = redis;
        this.json = json;
        this.properties = properties;
    }

    public void put(String campaignId, ProcessingResult result) {
        redis.opsForValue().set(
                key(campaignId, result.getRequestId()),
                json.write(result),
                properties.getResultCache().getTtl()
        );
    }

    public Optional<ProcessingResult> get(String campaignId, String requestId) {
        String value = redis.opsForValue().get(key(campaignId, requestId));
        return value == null ? Optional.empty() : Optional.of(json.read(value, ProcessingResult.class));
    }

    private String key(String campaignId, String requestId) {
        return "claim:request-result:{" + campaignId + "}:" + requestId;
    }
}
