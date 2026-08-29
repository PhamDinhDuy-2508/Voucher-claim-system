package com.example.voucherclaim.bootstrap;

import com.example.voucherclaim.config.AppProperties;
import com.example.voucherclaim.entity.UserScore;
import com.example.voucherclaim.repository.UserScoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Adds deterministic development score data without replacing scores that already exist. */
@Component
public class UserScoreSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(UserScoreSeeder.class);
    private static final int MAX_SEED_COUNT = 10_000;

    private final UserScoreRepository userScoreRepository;
    private final TransactionTemplate transactionTemplate;
    private final AppProperties properties;

    public UserScoreSeeder(
            UserScoreRepository userScoreRepository,
            TransactionTemplate transactionTemplate,
            AppProperties properties
    ) {
        this.userScoreRepository = userScoreRepository;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
    }

    /** Creates only missing rows, making repeated application starts safe. */
    @Override
    public void run(ApplicationArguments args) {
        AppProperties.UserScoreSeed seed = properties.getUserScoreSeed();
        if (!seed.isEnabled()) {
            log.info("User score startup seed is disabled");
            return;
        }
        validate(seed);

        int inserted = Objects.requireNonNull(transactionTemplate.execute(status -> seedMissing(seed)),
                "User score seed transaction returned no result");
        log.info("User score startup seed completed requested={} inserted={} startId={}",
                seed.getCount(), inserted, seed.getStartId());
    }

    /** Loads existing IDs in one query and persists only the missing deterministic rows. */
    private int seedMissing(AppProperties.UserScoreSeed seed) {
        List<String> userIds = new ArrayList<>(seed.getCount());
        for (int index = 0; index < seed.getCount(); index++) {
            userIds.add(String.format("%016d", seed.getStartId() + index));
        }

        Set<String> existingIds = new HashSet<>();
        userScoreRepository.findAllById(userIds)
                .forEach(userScore -> existingIds.add(userScore.getUserId()));

        List<UserScore> missing = new ArrayList<>();
        for (int index = 0; index < userIds.size(); index++) {
            String userId = userIds.get(index);
            if (!existingIds.contains(userId)) {
                // The stable formula gives the seed users a useful spread from 0 to 1000.
                long score = (index * 137L) % 1_001L;
                missing.add(new UserScore(userId, score));
            }
        }
        if (!missing.isEmpty()) {
            userScoreRepository.saveAll(missing);
            userScoreRepository.flush();
        }
        return missing.size();
    }

    private void validate(AppProperties.UserScoreSeed seed) {
        if (seed.getCount() < 0 || seed.getCount() > MAX_SEED_COUNT) {
            throw new IllegalStateException("app.user-score-seed.count must be between 0 and 10000");
        }
        if (seed.getStartId() < 0
                || seed.getStartId() + Math.max(seed.getCount() - 1L, 0L) > 9_999_999_999_999_999L) {
            throw new IllegalStateException("app.user-score-seed.start-id must produce 16-digit user IDs");
        }
    }
}
