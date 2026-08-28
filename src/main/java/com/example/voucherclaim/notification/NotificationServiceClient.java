package com.example.voucherclaim.notification;

/** Wrapper boundary for the external Notification Service. */
public interface NotificationServiceClient {
    /**
     * Sends one at-least-once message. Implementations must use eventId as their
     * idempotency key because a crash after acceptance can cause redelivery.
     */
    void send(NotificationMessage message);
}
