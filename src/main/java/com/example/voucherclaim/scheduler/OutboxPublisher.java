package com.example.voucherclaim.scheduler;

import com.example.voucherclaim.config.AppProperties;
import com.example.voucherclaim.domain.type.OutboxPublishStatus;
import com.example.voucherclaim.repository.OutboxRepository;
import com.example.voucherclaim.service.OutboxDeliveryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "app.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxRepository outboxRepository;
    private final OutboxDeliveryService deliveryService;
    private final AppProperties properties;

    public OutboxPublisher(
            OutboxRepository outboxRepository,
            OutboxDeliveryService deliveryService,
            AppProperties properties
    ) {
        this.outboxRepository = outboxRepository;
        this.deliveryService = deliveryService;
        this.properties = properties;
    }

    /** Polls a bounded set of pending IDs; each event owns a separate delivery transaction. */
    @Scheduled(fixedDelayString = "${app.outbox.poll-interval:500ms}")
    public void publishPending() {
        int batchSize = positive(properties.getOutbox().getBatchSize(), "batch-size");
        int maxRetries = positive(properties.getOutbox().getMaxRetries(), "max-retries");

        // Querying IDs keeps the poll transaction-free; deliver() rechecks status under row lock.
        var eventIds = outboxRepository.findPublishableEventIds(
                OutboxPublishStatus.PENDING,
                maxRetries,
                PageRequest.of(0, batchSize));
        if (!eventIds.isEmpty()) {
            log.debug("Outbox batch selected count={} maxRetries={}", eventIds.size(), maxRetries);
        }
        for (UUID eventId : eventIds) {
            deliveryService.deliver(eventId);
        }
    }

    /** Rejects invalid operational settings before constructing a pageable query. */
    private int positive(int value, String propertyName) {
        if (value < 1) {
            throw new IllegalStateException("app.outbox." + propertyName + " must be at least 1");
        }
        return value;
    }
}
