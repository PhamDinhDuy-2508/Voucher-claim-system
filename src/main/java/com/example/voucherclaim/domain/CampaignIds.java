package com.example.voucherclaim.domain;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

@Component
public class CampaignIds {
    private static final long TIMESTAMP_MASK = 0x0000_FFFF_FFFF_FFFFL;
    private static final long RANDOM_B_MASK = 0x3FFF_FFFF_FFFF_FFFFL;

    private final Clock clock;
    private final IntSupplier randomA;
    private final LongSupplier randomB;

    public CampaignIds() {
        this(
                Clock.systemUTC(),
                () -> ThreadLocalRandom.current().nextInt(1 << 12),
                () -> ThreadLocalRandom.current().nextLong()
        );
    }

    CampaignIds(Clock clock, IntSupplier randomA, LongSupplier randomB) {
        this.clock = clock;
        this.randomA = randomA;
        this.randomB = randomB;
    }

    /** Generates an RFC 9562 UUIDv7 using Unix epoch milliseconds and 74 random bits. */
    public String nextId() {
        long unixMillis = clock.millis() & TIMESTAMP_MASK;
        long randomAValue = randomA.getAsInt() & 0xFFFL;
        long randomBValue = randomB.getAsLong() & RANDOM_B_MASK;

        // Layout: 48-bit timestamp | version 7 | 12 random bits.
        long mostSignificantBits = (unixMillis << 16) | (0x7L << 12) | randomAValue;
        // RFC 4122 variant '10' followed by 62 random bits.
        long leastSignificantBits = (0x2L << 62) | randomBValue;
        return new UUID(mostSignificantBits, leastSignificantBits).toString();
    }
}
