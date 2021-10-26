package com.hedera.services.throttles;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class GasLimitDeterministicThrottleTest {

    private static final long ONE_SECOND_IN_NANOSECONDS = 1_000_000_000;

    @Test
    void usesZeroElapsedNanosOnFirstDecision() {
        // setup:
        long capacity = 1_000_000;
        long gasLimitForTX = 100_000;
        Instant now = Instant.ofEpochSecond(1_234_567L);

        // given:
        var subject = new GasLimitDeterministicThrottle(capacity);

        // when:
        var result = subject.allow(now, gasLimitForTX);

        // then:
        assertTrue(result);
        assertSame(now, subject.lastDecisionTime());
        assertEquals(capacity - gasLimitForTX, subject.delegate().bucket().capacityFree());
    }

    @Test
    void requiresMonotonicIncreasingTimeline() {
        // setup:
        long capacity = 1_000_000;
        long gasLimitForTX = 100_000;
        Instant now = Instant.ofEpochSecond(1_234_567L);

        // given:
        var subject = new GasLimitDeterministicThrottle(capacity);

        // when:
        subject.allow(now, gasLimitForTX);

        // then:
        assertThrows(IllegalArgumentException.class, () -> subject.allow(now.minusNanos(1), gasLimitForTX));
        assertDoesNotThrow(() -> subject.allow(now, gasLimitForTX));
    }

    @Test
    void usesCorrectElapsedNanosOnSubsequentDecision() {
        // setup:
        long capacity = 1_000_000;
        long gasLimitForTX = 100_000;

        double elapsed = 1_234;
        double toLeak = (elapsed / ONE_SECOND_IN_NANOSECONDS) * capacity;

        Instant originalDecision = Instant.ofEpochSecond(1_234_567L, 0);
        Instant now = Instant.ofEpochSecond(1_234_567L, (long)elapsed);

        // given:
        var subject = new GasLimitDeterministicThrottle(capacity);

        // when:
        subject.allow(originalDecision, gasLimitForTX);
        // and:
        var result = subject.allow(now, gasLimitForTX);

        // then:
        assertTrue(result);
        assertSame(now, subject.lastDecisionTime());
        assertEquals(
                (long)(capacity - gasLimitForTX - gasLimitForTX + toLeak),
                subject.delegate().bucket().capacityFree());
    }

    @Test
    void returnsExpectedState() {
        // setup:
        long capacity = 1_000_000;
        long gasLimitForTX = 100_000;
        Instant originalDecision = Instant.ofEpochSecond(1_234_567L, 0);

        // given:
        var subject = new GasLimitDeterministicThrottle(capacity);


        // when:
        subject.allow(originalDecision, gasLimitForTX);
        // and:
        var state = subject.usageSnapshot();

        // then:
        assertEquals(gasLimitForTX, state.used());
        assertEquals(originalDecision, state.lastDecisionTime());
    }

    @Test
    void resetsAsExpected() {
        // setup:
        long capacity = 1_000_000;
        long used = capacity / 2;
        Instant originalDecision = Instant.ofEpochSecond(1_234_567L, 0);

        // given:
        var subject = new GasLimitDeterministicThrottle(capacity);
        // and:
        var snapshot = new DeterministicThrottle.UsageSnapshot(used, originalDecision);

        // when:
        subject.resetUsageTo(snapshot);

        // then:
        assertEquals(used, subject.delegate().bucket().capacityUsed());
        assertEquals(originalDecision, subject.lastDecisionTime());
    }

}
