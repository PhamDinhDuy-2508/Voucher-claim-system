package com.example.voucherclaim.service.impl;

import com.example.voucherclaim.config.AppProperties;
import com.example.voucherclaim.domain.type.OutboxPublishStatus;
import com.example.voucherclaim.entity.OutboxEvent;
import com.example.voucherclaim.messaging.OutboxEventDispatcher;
import com.example.voucherclaim.repository.OutboxRepository;
import com.example.voucherclaim.service.OutboxDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

@Service
public class OutboxDeliveryServiceImpl implements OutboxDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(OutboxDeliveryServiceImpl.class);

    private final OutboxRepository outboxRepository;
    private final OutboxEventDispatcher eventDispatcher;
    private final AppProperties properties;
    private final TransactionTemplate transactionTemplate;

    public OutboxDeliveryServiceImpl(
            OutboxRepository outboxRepository,
            OutboxEventDispatcher eventDispatcher,
            AppProperties properties,
            TransactionTemplate transactionTemplate
    ) {
        this.outboxRepository = outboxRepository;
        this.eventDispatcher = eventDispatcher;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Locks and delivers one event. The row lock prevents two local/cluster publishers from
     * sending the same PENDING row concurrently; eventId still provides downstream idempotency.
     */
    @Override
    public void deliver(UUID eventId) {
        transactionTemplate.executeWithoutResult(status -> deliverInTransaction(eventId));
    }

    /** Owns the row lock and its publish-status mutation in one explicit transaction. */
    private void deliverInTransaction(UUID eventId) {
        OutboxEvent event = outboxRepository.lockById(eventId).orElse(null);
        if (event == null || event.getPublishStatus() != OutboxPublishStatus.PENDING) {
            log.debug("Outbox delivery skipped eventId={} reason=not-pending", eventId);
            return;
        }

        try {
            // PUBLISHED means Kafka durably acknowledged the message, not that a notification
            // has already reached the user. The consumer owns downstream delivery retries.
            eventDispatcher.dispatch(event);
            event.markPublished(Instant.now());
            log.debug("Outbox event published eventId={} eventType={}",
                    event.getEventId(), event.getEventType());
        } catch (RuntimeException deliveryFailure) {
            // The failure state commits in this transaction so the next poll can retry or stop.
            event.recordDeliveryFailure(maxRetries());
            log.warn(
                    "Kafka publish failed for outbox event {}, retryCount={}, status={}",
                    event.getEventId(),
                    event.getRetryCount(),
                    event.getPublishStatus(),
                    deliveryFailure
            );
        }
    }

    /** Validates retry configuration before it controls a persisted state transition. */
    private int maxRetries() {
        int maxRetries = properties.getOutbox().getMaxRetries();
        if (maxRetries < 1) {
            throw new IllegalStateException("app.outbox.max-retries must be at least 1");
        }
        return maxRetries;
    }
}
