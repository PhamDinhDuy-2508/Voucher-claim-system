package com.example.voucherclaim.entity;

import com.example.voucherclaim.domain.type.CampaignActivationJobStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CampaignActivationJobTest {
    @Test
    void advancesTheCursorAndCompletesOnlyAfterTheLastSlot() {
        Instant now = Instant.parse("2026-08-29T01:00:00Z");
        CampaignActivationJob job = new CampaignActivationJob("campaign-1", 1_500, now);

        assertThat(job.acquireLease("worker-1", now, now.plusSeconds(30))).isTrue();
        assertThat(job.completeBatch("worker-1", 1_001, now.plusMillis(10))).isFalse();
        assertThat(job.getStatus()).isEqualTo(CampaignActivationJobStatus.PENDING);
        assertThat(job.getNextSlotId()).isEqualTo(1_001);

        assertThat(job.acquireLease("worker-2", now.plusMillis(20), now.plusSeconds(30))).isTrue();
        assertThat(job.completeBatch("worker-2", 1_501, now.plusMillis(30))).isTrue();
        assertThat(job.getStatus()).isEqualTo(CampaignActivationJobStatus.COMPLETED);
    }

    @Test
    void recoversAnExpiredLeaseButRejectsAnActiveLease() {
        Instant now = Instant.parse("2026-08-29T01:00:00Z");
        CampaignActivationJob job = new CampaignActivationJob("campaign-1", 100, now);

        assertThat(job.acquireLease("worker-1", now, now.plusSeconds(30))).isTrue();
        assertThat(job.acquireLease("worker-2", now.plusSeconds(29), now.plusSeconds(59))).isFalse();
        assertThat(job.acquireLease("worker-2", now.plusSeconds(30), now.plusSeconds(60))).isTrue();
        assertThat(job.getLeaseOwner()).isEqualTo("worker-2");
    }
}
