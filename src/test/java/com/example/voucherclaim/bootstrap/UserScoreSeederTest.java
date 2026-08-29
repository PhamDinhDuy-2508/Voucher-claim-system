package com.example.voucherclaim.bootstrap;

import com.example.voucherclaim.config.AppProperties;
import com.example.voucherclaim.entity.UserScore;
import com.example.voucherclaim.repository.UserScoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserScoreSeederTest {
    private final UserScoreRepository repository = mock(UserScoreRepository.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final AppProperties properties = mock(AppProperties.class);
    private final UserScoreSeeder seeder = new UserScoreSeeder(repository, transactionTemplate, properties);

    @BeforeEach
    void executeTransactionCallbacksSynchronously() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    @Test
    void insertsOnlyMissingDeterministicUsers() throws Exception {
        when(properties.getUserScoreSeed()).thenReturn(
                new AppProperties.UserScoreSeed(true, 3, 2_000_000_000_000_001L));
        when(repository.findAllById(any())).thenReturn(List.of(
                new UserScore("2000000000000002", 999)));

        seeder.run(mock(ApplicationArguments.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserScore>> scores = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(scores.capture());
        verify(repository).flush();
        assertThat(scores.getValue())
                .extracting(UserScore::getUserId)
                .containsExactly("2000000000000001", "2000000000000003");
        assertThat(scores.getValue())
                .extracting(UserScore::getScore)
                .containsExactly(0L, 274L);
    }
}
