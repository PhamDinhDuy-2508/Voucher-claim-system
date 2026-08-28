package com.example.voucherclaim.domain;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CampaignIdsTest {
    @Test
    void generatesRfc9562UuidVersion7WithEmbeddedUnixMilliseconds() {
        long unixMillis = 1_725_000_000_123L;
        Clock clock = Clock.fixed(Instant.ofEpochMilli(unixMillis), ZoneOffset.UTC);
        UUID id = UUID.fromString(new CampaignIds(
                clock,
                () -> 0xABC,
                () -> 0x0123_4567_89AB_CDEFL
        ).nextId());

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
        assertThat((id.getMostSignificantBits() >>> 16) & 0x0000_FFFF_FFFF_FFFFL)
                .isEqualTo(unixMillis);
    }

    @Test
    void independentlyGeneratedIdsAreUnique() {
        CampaignIds ids = new CampaignIds();

        assertThat(ids.nextId()).isNotEqualTo(ids.nextId());
    }
}
