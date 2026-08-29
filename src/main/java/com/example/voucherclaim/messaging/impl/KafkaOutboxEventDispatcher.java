package com.example.voucherclaim.messaging.impl;

import com.example.voucherclaim.config.AppProperties;
import com.example.voucherclaim.entity.OutboxEvent;
import com.example.voucherclaim.messaging.OutboxEventDispatcher;
import com.example.voucherclaim.notification.NotificationMessage;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class KafkaOutboxEventDispatcher implements OutboxEventDispatcher {
    private static final Logger log = LoggerFactory.getLogger(KafkaOutboxEventDispatcher.class);
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AppProperties properties;

    public KafkaOutboxEventDispatcher(KafkaTemplate<String, Object> kafkaTemplate,
                                      AppProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    /** Publishes durable claim outcome notifications and waits for the broker ack. */
    @Override
    public void dispatch(OutboxEvent event) {
        if (!"VoucherClaimed".equals(event.getEventType())
                && !"VoucherClaimRejected".equals(event.getEventType())) {
            throw new IllegalArgumentException("Unsupported outbox event type: " + event.getEventType());
        }
        NotificationMessage message = NotificationMessage.from(event);
        String topic = properties.getKafka().getNotificationTopic();

        try {
            kafkaTemplate.send(topic, event.getEventId().toString(), message)
                    .get(properties.getKafka().getSendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            log.debug("Kafka acknowledged outbox event eventId={} eventType={} topic={}",
                    event.getEventId(), event.getEventType(), topic);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing outbox event", interrupted);
        } catch (ExecutionException | TimeoutException publishFailure) {
            throw new IllegalStateException("Kafka did not acknowledge outbox event", publishFailure);
        }
    }
}
