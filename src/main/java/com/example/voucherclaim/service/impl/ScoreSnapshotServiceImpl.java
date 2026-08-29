package com.example.voucherclaim.service.impl;

import com.example.voucherclaim.entity.UserScore;
import com.example.voucherclaim.redis.ScoreSnapshotStore;
import com.example.voucherclaim.repository.UserScoreRepository;
import com.example.voucherclaim.service.ScoreSnapshotService;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.OptionalLong;

@Service
public class ScoreSnapshotServiceImpl implements ScoreSnapshotService {
    private static final Logger log = LoggerFactory.getLogger(ScoreSnapshotServiceImpl.class);
    private final ScoreSnapshotStore scoreSnapshotStore;
    private final UserScoreRepository userScoreRepository;
    private final TransactionTemplate transactionTemplate;

    public ScoreSnapshotServiceImpl(
            ScoreSnapshotStore scoreSnapshotStore,
            UserScoreRepository userScoreRepository,
            TransactionTemplate transactionTemplate
    ) {
        this.scoreSnapshotStore = scoreSnapshotStore;
        this.userScoreRepository = userScoreRepository;
        this.transactionTemplate = transactionTemplate;
    }

    /** Uses Redis first and rebuilds the cache from MySQL when the key is missing. */
    @Override
    public OptionalLong get(String userId) {
        try {
            OptionalLong cached = scoreSnapshotStore.get(userId);
            if (cached.isPresent()) {
                log.debug("Priority score cache hit userId={} score={}", userId, cached.getAsLong());
                return cached;
            }
        } catch (RuntimeException cacheFailure) {
            log.warn("Priority score cache read failed; falling back to MySQL userId={}",
                    userId, cacheFailure);
        }

        OptionalLong durable = userScoreRepository.findById(userId)
                .map(user -> OptionalLong.of(user.getScore()))
                .orElseGet(OptionalLong::empty);
        durable.ifPresent(score -> warmCache(userId, score));
        log.debug("Priority score loaded from MySQL userId={} found={}", userId, durable.isPresent());
        return durable;
    }

    /** Commits the source of truth first so a Redis failure cannot lose the score update. */
    @Override
    public void put(String userId, long score) {
        validateScore(score);
        UserScore saved = Objects.requireNonNull(transactionTemplate.execute(status -> {
            // findById + save keeps creation and subsequent score replacement behind JPA.
            UserScore user = userScoreRepository.findById(userId)
                    .orElseGet(() -> new UserScore(userId, score));
            user.updateScore(score);
            UserScore persisted = userScoreRepository.save(user);
            userScoreRepository.flush();
            return persisted;
        }));

        // Cache publication happens only after the database callback completes successfully.
        warmCache(saved.getUserId(), saved.getScore());
        log.info("Priority score persisted userId={} score={} version={}",
                saved.getUserId(), saved.getScore(), saved.getVersion());
    }

    /** Keeps Redis optional: a later cache miss can always rebuild from MySQL. */
    private void warmCache(String userId, long score) {
        try {
            scoreSnapshotStore.put(userId, score);
        } catch (RuntimeException cacheFailure) {
            log.warn("Priority score cache write failed; MySQL remains authoritative userId={}",
                    userId, cacheFailure);
        }
    }

    private void validateScore(long score) {
        if (score < 0 || score > 1_000_000_000L) {
            throw new IllegalArgumentException("score must be between 0 and 1,000,000,000");
        }
    }
}
