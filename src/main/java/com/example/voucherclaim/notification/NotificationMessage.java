package com.example.voucherclaim.notification;

import com.example.voucherclaim.entity.OutboxEvent;

import java.util.Map;
import java.util.UUID;

public class NotificationMessage {
    private UUID eventId;
    private String eventType;
    private UUID aggregateId;
    private Map<String, Object> payload;

    /** Required by Kafka's JSON deserializer. */
    public NotificationMessage() {
    }

    public NotificationMessage(
            UUID eventId,
            String eventType,
            UUID aggregateId,
            Map<String, Object> payload
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
    }

    /** Maps the generic outbox envelope to the Notification Service boundary contract. */
    public static NotificationMessage from(OutboxEvent event) {
        return new NotificationMessage(
                event.getEventId(),
                event.getEventType(),
                event.getAggregateId(),
                event.getPayload()
        );
    }

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public UUID getAggregateId() { return aggregateId; }
    public void setAggregateId(UUID aggregateId) { this.aggregateId = aggregateId; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
}
