package com.example.voucherclaim.repository;

import com.example.voucherclaim.domain.type.OutboxPublishStatus;
import com.example.voucherclaim.entity.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    @Query("""
            select event.eventId
            from OutboxEvent event
            where event.publishStatus = :status
              and event.retryCount < :maxRetries
            order by event.createdAt, event.eventId
            """)
    List<UUID> findPublishableEventIds(
            @Param("status") OutboxPublishStatus status,
            @Param("maxRetries") int maxRetries,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from OutboxEvent event where event.eventId = :eventId")
    Optional<OutboxEvent> lockById(@Param("eventId") UUID eventId);
}
