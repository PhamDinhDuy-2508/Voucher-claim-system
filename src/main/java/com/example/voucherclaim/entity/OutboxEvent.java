package com.example.voucherclaim.entity;

import com.example.voucherclaim.domain.type.OutboxPublishStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {
    @Id
    @Column(name = "event_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "publish_status", nullable = false, length = 32)
    private OutboxPublishStatus publishStatus;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(
            UUID eventId,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            Map<String, Object> payload,
            OutboxPublishStatus publishStatus,
            int retryCount,
            Instant createdAt
    ) {
        this.eventId = eventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.publishStatus = publishStatus;
        this.retryCount = retryCount;
        this.createdAt = createdAt;
    }

    public UUID getEventId() { return eventId; }
    public String getAggregateType() { return aggregateType; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public Map<String, Object> getPayload() { return payload; }
    /** Marks the event terminal only after the Kafka broker acknowledges it. */
    public void markPublished(Instant publishedAt) {
        this.publishStatus = OutboxPublishStatus.PUBLISHED;
        this.publishedAt = publishedAt;
    }

    /** Records a failed attempt and moves the event to dead-letter at the retry limit. */
    public void recordDeliveryFailure(int maxRetries) {
        retryCount++;
        if (retryCount >= maxRetries) {
            publishStatus = OutboxPublishStatus.DEAD_LETTER;
        }
    }

    public OutboxPublishStatus getPublishStatus() { return publishStatus; }
    public int getRetryCount() { return retryCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
}
