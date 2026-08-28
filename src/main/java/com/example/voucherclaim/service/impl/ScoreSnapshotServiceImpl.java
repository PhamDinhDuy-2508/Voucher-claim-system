package com.example.voucherclaim.service.impl;

import com.example.voucherclaim.redis.ScoreSnapshotStore;
import com.example.voucherclaim.service.ScoreSnapshotService;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.OptionalLong;

@Service
public class ScoreSnapshotServiceImpl implements ScoreSnapshotService {
    private static final Logger log = LoggerFactory.getLogger(ScoreSnapshotServiceImpl.class);
    private final ScoreSnapshotStore scoreSnapshotStore;

    public ScoreSnapshotServiceImpl(ScoreSnapshotStore scoreSnapshotStore) {
        this.scoreSnapshotStore = scoreSnapshotStore;
    }

    @Override
    public OptionalLong get(String campaignId, String userId) {
        OptionalLong score = scoreSnapshotStore.get(campaignId, userId);
        log.debug("Priority score read campaignId={} userId={} hit={}",
                campaignId, userId, score.isPresent());
        return score;
    }

    @Override
    public void put(String campaignId, String userId, long score) {
        scoreSnapshotStore.put(campaignId, userId, score);
        log.debug("Priority score stored campaignId={} userId={} score={}",
                campaignId, userId, score);
    }
}
