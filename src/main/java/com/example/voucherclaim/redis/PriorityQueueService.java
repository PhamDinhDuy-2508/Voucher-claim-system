package com.example.voucherclaim.redis;

import com.example.voucherclaim.config.AppProperties;
import com.example.voucherclaim.domain.type.QueueAdmissionResult;
import com.example.voucherclaim.model.PriorityRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class PriorityQueueService {
    private static final String ACTIVE_CAMPAIGNS_KEY = "claim:priority:active-campaigns";
    // The Lua script makes duplicate detection, queue-capacity enforcement, insert and TTL
    // one atomic Redis operation. Return values: 1=added, 0=already pending, -1=full.
    private static final DefaultRedisScript<Long> ENQUEUE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('ZSCORE', KEYS[1], ARGV[2]) then
                return 0
            end
            if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[3]) then
                return -1
            end
            redis.call('ZADD', KEYS[1], 'NX', ARGV[1], ARGV[2])
            redis.call('PEXPIRE', KEYS[1], ARGV[4])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final AppProperties properties;

    public PriorityQueueService(StringRedisTemplate redis, AppProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public QueueAdmissionResult enqueue(PriorityRequest request) {
        String queueKey = queueKey(request.getCampaignId());
        Long result = redis.execute(
                ENQUEUE_SCRIPT,
                Collections.singletonList(queueKey),
                Long.toString(request.getScoreSnapshot()),
                request.member(),
                Long.toString(properties.getPriority().getMaxPendingPerCampaign()),
                Long.toString(properties.getPriority().getQueueKeyGrace().toMillis())
        );
        long value = Objects.requireNonNullElse(result, -1L);
        if (value == 1L) {
            // The scheduler scans only campaigns with pending work instead of all campaigns.
            redis.opsForSet().add(ACTIVE_CAMPAIGNS_KEY, request.getCampaignId());
            return QueueAdmissionResult.ADDED;
        }
        if (value == 0L) {
            return QueueAdmissionResult.ALREADY_PENDING;
        }
        return QueueAdmissionResult.FULL;
    }

    public List<PriorityRequest> popHighest(String campaignId, int count) {
        // ZPOPMAX atomically removes the highest scores, so two scheduler instances cannot
        // dispatch the same queue member.
        Set<ZSetOperations.TypedTuple<String>> tuples = redis.opsForZSet().popMax(queueKey(campaignId), count);
        if (tuples == null || tuples.isEmpty()) {
            redis.opsForSet().remove(ACTIVE_CAMPAIGNS_KEY, campaignId.toString());
            return List.of();
        }
        if (Boolean.FALSE.equals(redis.hasKey(queueKey(campaignId)))) {
            redis.opsForSet().remove(ACTIVE_CAMPAIGNS_KEY, campaignId.toString());
        }
        return tuples.stream()
                .map(tuple -> PriorityRequest.fromMember(
                        campaignId,
                        Objects.requireNonNull(tuple.getValue()),
                        Objects.requireNonNull(tuple.getScore())
                ))
                .toList();
    }

    public Set<String> activeCampaignIds() {
        Set<String> campaigns = redis.opsForSet().members(ACTIVE_CAMPAIGNS_KEY);
        return campaigns == null ? Set.of() : campaigns;
    }

    public long depth(String campaignId) {
        Long size = redis.opsForZSet().size(queueKey(campaignId));
        return size == null ? 0L : size;
    }

    private String queueKey(String campaignId) {
        return "claim:priority:{" + campaignId + "}";
    }

}
