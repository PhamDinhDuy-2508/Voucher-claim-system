package com.example.voucherclaim.messaging;

import com.example.voucherclaim.entity.OutboxEvent;

import java.util.Map;
import java.util.UUID;

public class ClaimRequestMessage {
    private UUID eventId;
    private String requestId;

    public ClaimRequestMessage() {
    }

    public ClaimRequestMessage(UUID eventId, String requestId) {
        this.eventId = eventId;
        this.requestId = requestId;
    }

    public static ClaimRequestMessage from(OutboxEvent event) {
        Map<String, Object> payload = event.getPayload();
        return new ClaimRequestMessage(event.getEventId(), String.valueOf(payload.get("request_id")));
    }

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
