package com.example.voucherclaim.entity;

import com.example.voucherclaim.domain.type.ClaimRequestStatus;
import com.example.voucherclaim.domain.type.ProcessingResultType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimRequestTest {
    @Test
    void leaseExpiryReturnsProcessingRequestToRetryPath() {
        Instant createdAt = Instant.parse("2026-08-27T00:00:00Z");
        ClaimRequest request = request(createdAt);
        assertThat(request.acquireLease("worker-1", createdAt, createdAt.plusSeconds(5))).isTrue();

        assertThat(request.recoverExpiredLease(createdAt.plusSeconds(6))).isTrue();

        assertThat(request.getStatus()).isEqualTo(ClaimRequestStatus.RETRY_WAIT);
        assertThat(request.getLeaseOwner()).isNull();
        assertThat(request.isReady(createdAt.plusSeconds(6))).isTrue();
    }

    @Test
    void terminalResultCannotBeAcquiredAgain() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        ClaimRequest request = request(now);
        request.complete(ProcessingResultType.SOLD_OUT, null, "sold out", now);

        assertThat(request.getStatus()).isEqualTo(ClaimRequestStatus.REJECTED);
        assertThat(request.acquireLease("worker-2", now, now.plusSeconds(5))).isFalse();
    }

    @Test
    void staleLeaseOwnerCannotOverwriteCurrentProcessingState() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        ClaimRequest request = request(now);
        request.acquireLease("worker-new", now, now.plusSeconds(5));

        assertThat(request.completeIfOwned(
                "worker-old", ProcessingResultType.SOLD_OUT, null, "stale", now)).isFalse();

        assertThat(request.getStatus()).isEqualTo(ClaimRequestStatus.PROCESSING);
        assertThat(request.getResultType()).isNull();
    }

    @Test
    void queuedRequestCanBeLeasedBeforeRecoveryRecheckTime() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        ClaimRequest request = request(now);
        request.markQueued(now.plusSeconds(5), now);

        assertThat(request.acquireLease("worker-1", now, now.plusSeconds(5))).isTrue();
        assertThat(request.getStatus()).isEqualTo(ClaimRequestStatus.PROCESSING);
    }

    private ClaimRequest request(Instant now) {
        return new ClaimRequest("a".repeat(64), "019c6fa6-5e22-7abc-9123-abcdef123456", "2000000000000001",
                900L, 10, now);
    }
}
