package com.example.voucherclaim.repository;

import com.example.voucherclaim.domain.type.ClaimRequestStatus;
import com.example.voucherclaim.entity.ClaimRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClaimRequestRepository extends JpaRepository<ClaimRequest, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from ClaimRequest request where request.requestId = :requestId")
    Optional<ClaimRequest> lockById(@Param("requestId") String requestId);

    /** Atomically assigns one eligible request to one worker without loading the entity first. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ClaimRequest request
            set request.status = :processingStatus,
                request.leaseOwner = :owner,
                request.leaseUntil = :leaseUntil,
                request.attempt = request.attempt + 1,
                request.updatedAt = :now
            where request.requestId = :requestId
              and request.attempt < request.maxAttempt
              and (
                    request.status = :queuedStatus
                    or (
                        request.status in :dueStatuses
                        and request.nextAttemptAt <= :now
                    )
              )
            """)
    int acquireLeaseAtomically(
            @Param("requestId") String requestId,
            @Param("owner") String owner,
            @Param("now") Instant now,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("processingStatus") ClaimRequestStatus processingStatus,
            @Param("queuedStatus") ClaimRequestStatus queuedStatus,
            @Param("dueStatuses") Collection<ClaimRequestStatus> dueStatuses
    );

    @Query("""
            select request.requestId
            from ClaimRequest request
            where (request.status in :readyStatuses and request.nextAttemptAt <= :now)
               or (request.status = :processingStatus and request.leaseUntil < :now)
            order by request.nextAttemptAt, request.createdAt, request.requestId
            """)
    List<String> findRecoverableIds(
            @Param("readyStatuses") Collection<ClaimRequestStatus> readyStatuses,
            @Param("processingStatus") ClaimRequestStatus processingStatus,
            @Param("now") Instant now,
            Pageable pageable
    );
}
