package com.example.voucherclaim.service.impl;

import com.example.voucherclaim.entity.UserScore;
import com.example.voucherclaim.redis.ScoreSnapshotStore;
import com.example.voucherclaim.repository.UserScoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScoreSnapshotServiceImplTest {
    private final ScoreSnapshotStore cache = mock(ScoreSnapshotStore.class);
    private final UserScoreRepository repository = mock(UserScoreRepository.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final ScoreSnapshotServiceImpl service =
            new ScoreSnapshotServiceImpl(cache, repository, transactionTemplate);

    @BeforeEach
    void executeTransactionCallbacksSynchronously() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    @Test
    void returnsCachedScoreWithoutReadingMySql() {
        when(cache.get("user-1")).thenReturn(OptionalLong.of(900));

        assertThat(service.get("user-1")).hasValue(900);

        verify(repository, never()).findById(any());
    }

    @Test
    void rebuildsRedisWhenScoreIsLoadedFromMySql() {
        UserScore user = new UserScore("user-1", 800);
        when(cache.get("user-1")).thenReturn(OptionalLong.empty());
        when(repository.findById("user-1")).thenReturn(Optional.of(user));

        assertThat(service.get("user-1")).hasValue(800);

        verify(cache).put("user-1", 800);
    }

    @Test
    void persistsScoreBeforeRefreshingRedis() {
        UserScore user = new UserScore("user-1", 100);
        when(repository.findById("user-1")).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);

        service.put("user-1", 950);

        var ordered = inOrder(repository, cache);
        ordered.verify(repository).save(user);
        ordered.verify(repository).flush();
        ordered.verify(cache).put("user-1", 950);
        assertThat(user.getScore()).isEqualTo(950);
    }
}
