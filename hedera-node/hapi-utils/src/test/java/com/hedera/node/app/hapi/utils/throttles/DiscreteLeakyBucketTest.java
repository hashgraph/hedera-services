/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.hapi.utils.throttles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DiscreteLeakyBucketTest {
    private long totalCapacity = 64_000L;
    private long capacityUsed = totalCapacity / 4;

    @Test
    void requiresPositiveCapacity() {
        // expect:
        assertThrows(IllegalArgumentException.class, () -> new DiscreteLeakyBucket(-1L));
        assertThrows(IllegalArgumentException.class, () -> new DiscreteLeakyBucket(0L));
    }

    @Test
    void startsEmptyIfNoInitialUsageGiven() {
        // given:
        var subject = new DiscreteLeakyBucket(totalCapacity);

        // expect:
        assertEquals(0L, subject.capacityUsed());
        assertEquals(totalCapacity, subject.totalCapacity());
        assertEquals(totalCapacity, subject.capacityFree());
    }

    @Test
    void assertCapacityAndUsed() {
        // given:
        var subject = new DiscreteLeakyBucket(1234L, totalCapacity);

        // expect:
        assertEquals(totalCapacity, subject.totalCapacity());
        assertEquals(1234L, subject.capacityUsed());
    }

    @Test
    void leaksAsExpected() {
        // given:
        var subject = new DiscreteLeakyBucket(capacityUsed, totalCapacity);

        // when:
        subject.leak(capacityUsed);

        // then:
        assertEquals(0, subject.capacityUsed());
        assertEquals(totalCapacity, subject.capacityFree());
    }

    @Test
    void leaksToEmptyButNeverMore() {
        // given:
        var subject = new DiscreteLeakyBucket(capacityUsed, totalCapacity);

        // when:
        subject.leak(Long.MAX_VALUE);

        // then:
        assertEquals(0L, subject.capacityUsed());
    }

    @Test
    void mustHaveNonZeroCapacity() {
        // expect:
        assertThrows(IllegalArgumentException.class, () -> new DiscreteLeakyBucket(0, 0));
    }

    @Test
    void cannotLeakNegativeUnits() {
        // given:
        var subject = new DiscreteLeakyBucket(capacityUsed, totalCapacity);

        // when:
        assertThrows(IllegalArgumentException.class, () -> subject.leak(-1));
    }

    @Test
    void prohibitsNegativeUse() {
        // given:
        var subject = new DiscreteLeakyBucket(capacityUsed, totalCapacity);

        // when:
        assertThrows(IllegalArgumentException.class, () -> subject.useCapacity(-1));
    }

    @Test
    void prohibitsExcessUsageViaOverflow() {
        // given:
        var subject = new DiscreteLeakyBucket(capacityUsed, totalCapacity);
        // and:
        var overflowAmount = Long.MAX_VALUE - capacityUsed + 1L;

        // when:
        assertThrows(IllegalArgumentException.class, () -> subject.useCapacity(overflowAmount));
    }

    @Test
    void prohibitsExcessUsage() {
        // given:
        var subject = new DiscreteLeakyBucket(capacityUsed, totalCapacity);

        // when:
        assertThrows(IllegalArgumentException.class, () -> subject.useCapacity(1 + totalCapacity - capacityUsed));
    }

    @Test
    void permitsUse() {
        // given:
        var subject = new DiscreteLeakyBucket(capacityUsed, totalCapacity);

        // when:
        subject.useCapacity(totalCapacity - capacityUsed);

        // then:
        assertEquals(totalCapacity, subject.capacityUsed());
    }

    @Test
    void permitsResettingUsedAmount() {
        // given:
        var subject = new DiscreteLeakyBucket(capacityUsed, totalCapacity);

        // when:
        subject.resetUsed(1L);

        // then:
        assertEquals(1L, subject.capacityUsed());
    }

    @Test
    void rejectsNonsenseUsage() {
        // given:
        var subject = new DiscreteLeakyBucket(totalCapacity);

        // expect:
        assertThrows(IllegalArgumentException.class, () -> subject.resetUsed(-1L));
        assertThrows(IllegalArgumentException.class, () -> subject.resetUsed(totalCapacity + 1L));
    }
}
