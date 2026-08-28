package com.example.voucherclaim.notification.impl;

import com.example.voucherclaim.notification.NotificationMessage;
import com.example.voucherclaim.notification.NotificationServiceClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Local Notification Service client used by the Kafka consumer in this runnable starter.
 * Replace it with the real notification provider integration in production.
 */
@Component
public class LoggingNotificationServiceClient implements NotificationServiceClient {
    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationServiceClient.class);

    /** A successful return lets the Kafka listener commit this record's offset. */
    @Override
    public void send(NotificationMessage message) {
        // Do not log the complete payload because it may later contain sensitive user data.
        log.info(
                "Notification accepted: eventId={}, eventType={}, aggregateId={}",
                message.getEventId(),
                message.getEventType(),
                message.getAggregateId()
        );
    }
}
