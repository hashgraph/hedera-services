/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.services.throttles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GasLimitDeterministicThrottleTest {

    private static final long DEFAULT_CAPACITY = 1_000_000;
    private static final long ONE_SECOND_IN_NANOSECONDS = 1_000_000_000;

    GasLimitDeterministicThrottle subject;

    @BeforeEach
    void setup() {
        subject = new GasLimitDeterministicThrottle(DEFAULT_CAPACITY);
    }

    @Test
    void usesZeroElapsedNanosOnFirstDecision() {
        // setup:
        long gasLimitForTX = 100_000;
        Instant now = Instant.ofEpochSecond(1_234_567L);

        // when:
        var result = subject.allow(now, gasLimitForTX);

        // then:
        assertTrue(result);
        assertEquals(DEFAULT_CAPACITY - gasLimitForTX, subject.delegate().bucket().capacityFree());
    }

    @Test
    void canGetPercentUsed() {
        final var now = Instant.ofEpochSecond(1_234_567L);
        final var capacity = 1_000_000;
        final var subject = new GasLimitDeterministicThrottle(capacity);
        assertEquals(0.0, subject.percentUsed(now));
        subject.allow(now, capacity / 2);
        assertEquals(50.0, subject.percentUsed(now));
        assertEquals(50.0, subject.percentUsed(now.minusNanos(123)));
    }

    @Test
    void canGetFreeToUsedRatio() {
        final var now = Instant.ofEpochSecond(1_234_567L);
        final var capacity = 1_000_000;
        final var subject = new GasLimitDeterministicThrottle(capacity);
        subject.allow(now, capacity / 4);
        assertEquals(3, subject.freeToUsedRatio(now));
    }

    @Test
    void leaksUntilNowBeforeEstimatingFreeToUsed() {
        final var now = Instant.ofEpochSecond(1_234_567L);
        final var then = now.plusSeconds(1234);
        final var capacity = 1_000_000;
        final var subject = new GasLimitDeterministicThrottle(capacity);
        subject.allow(now, capacity / 4);
        assertEquals(Long.MAX_VALUE, subject.freeToUsedRatio(then));
        assertEquals(then, subject.getLastDecisionTime());
    }

    @Test
    void requiresMonotonicIncreasingTimeline() {
        // setup:
        long gasLimitForTX = 100_000;
        Instant now = Instant.ofEpochSecond(1_234_567L);
        Instant illegal = now.minusNanos(1);

        // when:
        subject.allow(now, gasLimitForTX);

        // then:
        assertThrows(IllegalArgumentException.class, () -> subject.allow(illegal, gasLimitForTX));
        assertDoesNotThrow(() -> subject.allow(now, gasLimitForTX));
    }

    @Test
    void usesCorrectElapsedNanosOnSubsequentDecision() {
        // setup:
        long gasLimitForTX = 100_000;

        double elapsed = 1_234;
        double toLeak = (elapsed / ONE_SECOND_IN_NANOSECONDS) * DEFAULT_CAPACITY;

        Instant originalDecision = Instant.ofEpochSecond(1_234_567L, 0);
        Instant now = Instant.ofEpochSecond(1_234_567L, (long) elapsed);

        // when:
        subject.allow(originalDecision, gasLimitForTX);
        // and:
        var result = subject.allow(now, gasLimitForTX);

        // then:
        assertTrue(result);
        assertEquals(
                (long) (DEFAULT_CAPACITY - gasLimitForTX - gasLimitForTX + toLeak),
                subject.delegate().bucket().capacityFree());
    }

    @Test
    void capacityReturnsCorrectValue() {
        assertEquals(DEFAULT_CAPACITY, subject.getCapacity());
    }

    @Test
    void usedReturnsCorrectValue() {
        assertEquals(0, subject.getUsed());
    }

    @Test
    void verifyLeakUnusedGas() {
        subject.allow(Instant.now(), 100L);
        assertEquals(999_900L, subject.delegate().bucket().capacityFree());

        subject.leakUnusedGasPreviouslyReserved(100L);
        assertEquals(DEFAULT_CAPACITY, subject.delegate().bucket().capacityFree());
    }

    @Test
    void returnsExpectedState() {
        final var originalDecision = Instant.ofEpochSecond(1_234_567L, 0);

        subject.allow(originalDecision, 1234);
        final var state = subject.usageSnapshot();

        assertEquals(1234, state.used());
        assertEquals(originalDecision, state.lastDecisionTime());
    }

    @Test
    void resetsUsageToAsExpected() {
        final long used = DEFAULT_CAPACITY / 2;
        final var originalDecision = Instant.ofEpochSecond(1_234_567L, 0);
        final var snapshot = new DeterministicThrottle.UsageSnapshot(used, originalDecision);

        subject.resetUsageTo(snapshot);

        assertEquals(used, subject.delegate().bucket().capacityUsed());
        assertEquals(snapshot, subject.usageSnapshot());
    }

    @Test
    void resetsUsageAsExpected() {
        // setup:
        long gasLimitForTX = 100_000;
        Instant now = Instant.ofEpochSecond(1_234_567L);

        // when:
        var result = subject.allow(now, gasLimitForTX);
        subject.resetUsage();

        // then:
        assertTrue(result);
        assertEquals(DEFAULT_CAPACITY, subject.delegate().bucket().capacityFree());
    }

    @Test
    void reclaimsLastAllowedUseAsExpected() {
        // setup:
        long gasLimitForTX = 100_000;
        Instant now = Instant.ofEpochSecond(1_234_567L);

        // when:
        var result = subject.allow(now, gasLimitForTX);
        subject.resetLastAllowedUse();
        var result2 = subject.allow(now, gasLimitForTX);
        subject.reclaimLastAllowedUse();

        // then:
        assertTrue(result);
        assertTrue(result2);
        assertEquals(gasLimitForTX, subject.getUsed());
        assertEquals(DEFAULT_CAPACITY - gasLimitForTX, subject.delegate().bucket().capacityFree());
    }

    @Test
    void resetsLastAllowedUseAsExpected() {
        // setup:
        long gasLimitForTX = 100_000;
        Instant now = Instant.ofEpochSecond(1_234_567L);

        // when:
        var result = subject.allow(now, gasLimitForTX);
        var result2 = subject.allow(now, gasLimitForTX);
        subject.resetLastAllowedUse();
        subject.reclaimLastAllowedUse();

        // then:
        assertTrue(result);
        assertTrue(result2);
        assertEquals(
                DEFAULT_CAPACITY - (gasLimitForTX * 2), subject.delegate().bucket().capacityFree());
    }
}
