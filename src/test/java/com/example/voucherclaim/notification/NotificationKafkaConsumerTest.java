package com.example.voucherclaim.notification;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationKafkaConsumerTest {
    @Test
    void delegatesConsumedMessageToNotificationService() {
        NotificationServiceClient client = mock(NotificationServiceClient.class);
        NotificationKafkaConsumer consumer = new NotificationKafkaConsumer(client);
        NotificationMessage message = new NotificationMessage(
                UUID.randomUUID(),
                "VoucherClaimed",
                UUID.randomUUID(),
                Map.of("status", "ISSUED")
        );

        consumer.consume(message);

        verify(client).send(message);
    }
}
