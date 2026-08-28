package com.example.voucherclaim.notification;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Local runnable representation of the Notification Service consumer. In production this
 * class can move to a separately deployed Notification Service without changing the producer.
 */
@Component
public class NotificationKafkaConsumer {
    private final NotificationServiceClient notificationServiceClient;

    public NotificationKafkaConsumer(NotificationServiceClient notificationServiceClient) {
        this.notificationServiceClient = notificationServiceClient;
    }

    /** A thrown exception leaves the Kafka record uncommitted so it can be redelivered. */
    @KafkaListener(
            topics = "${app.kafka.notification-topic:voucher.notifications}",
            groupId = "${spring.kafka.consumer.group-id:voucher-notification-service}"
    )
    public void consume(NotificationMessage message) {
        notificationServiceClient.send(message);
    }
}
