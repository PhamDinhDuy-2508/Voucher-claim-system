package com.example.voucherclaim.service.impl;

import com.example.voucherclaim.config.AppProperties;
import com.example.voucherclaim.domain.type.OutboxPublishStatus;
import com.example.voucherclaim.entity.OutboxEvent;
import com.example.voucherclaim.messaging.OutboxEventDispatcher;
import com.example.voucherclaim.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxDeliveryServiceImplTest {
    @Mock OutboxRepository outboxRepository;
    @Mock OutboxEventDispatcher eventDispatcher;
    @Mock AppProperties properties;
    @Mock AppProperties.Outbox outboxProperties;
    @Mock TransactionTemplate transactionTemplate;

    private OutboxDeliveryServiceImpl service;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            invocation.<java.util.function.Consumer<TransactionStatus>>getArgument(0)
                    .accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        service = new OutboxDeliveryServiceImpl(
                outboxRepository, eventDispatcher, properties, transactionTemplate);
    }

    @Test
    void marksEventPublishedAfterKafkaAcknowledgesTheMessage() {
        OutboxEvent event = pendingEvent();
        when(outboxRepository.lockById(event.getEventId())).thenReturn(Optional.of(event));

        service.deliver(event.getEventId());

        verify(eventDispatcher).dispatch(event);
        assertThat(event.getPublishStatus()).isEqualTo(OutboxPublishStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getRetryCount()).isZero();
    }

    @Test
    void keepsEventPendingWhenDeliveryCanBeRetried() {
        OutboxEvent event = pendingEvent();
        when(outboxRepository.lockById(event.getEventId())).thenReturn(Optional.of(event));
        when(properties.getOutbox()).thenReturn(outboxProperties);
        when(outboxProperties.getMaxRetries()).thenReturn(3);
        doThrow(new IllegalStateException("kafka unavailable"))
                .when(eventDispatcher).dispatch(any(OutboxEvent.class));

        service.deliver(event.getEventId());

        assertThat(event.getPublishStatus()).isEqualTo(OutboxPublishStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getPublishedAt()).isNull();
    }

    @Test
    void movesEventToDeadLetterAfterRetryBudgetIsExhausted() {
        OutboxEvent event = pendingEvent();
        when(outboxRepository.lockById(event.getEventId())).thenReturn(Optional.of(event));
        when(properties.getOutbox()).thenReturn(outboxProperties);
        when(outboxProperties.getMaxRetries()).thenReturn(1);
        doThrow(new IllegalStateException("kafka unavailable"))
                .when(eventDispatcher).dispatch(any(OutboxEvent.class));

        service.deliver(event.getEventId());

        assertThat(event.getPublishStatus()).isEqualTo(OutboxPublishStatus.DEAD_LETTER);
        assertThat(event.getRetryCount()).isEqualTo(1);
    }

    private OutboxEvent pendingEvent() {
        UUID claimId = UUID.randomUUID();
        return new OutboxEvent(
                UUID.randomUUID(),
                "VoucherClaim",
                claimId,
                "VoucherClaimed",
                Map.of("claim_id", claimId.toString()),
                OutboxPublishStatus.PENDING,
                0,
                Instant.parse("2026-08-27T00:00:00Z")
        );
    }
}
