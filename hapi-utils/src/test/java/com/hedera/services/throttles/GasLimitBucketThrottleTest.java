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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.utility.Units;
import org.junit.jupiter.api.Test;

class GasLimitBucketThrottleTest {

    private static final long BUCKET_CAPACITY = 1_234_568;
    private static final long BEYOND_CAPACITY = 1_234_569;

    GasLimitBucketThrottle subject = new GasLimitBucketThrottle(BUCKET_CAPACITY);

    @Test
    void hasExpectedPercentUsed() {
        final var capacity = 1_000_000;
        var subject = new GasLimitBucketThrottle(capacity);
        subject.bucket().useCapacity(capacity / 2);

        assertEquals(50.0, subject.percentUsed(0));
        assertEquals(25.0, subject.percentUsed(Units.SECONDS_TO_NANOSECONDS / 4));
    }

    @Test
    void hasExpectedUsageRatio() {
        final var capacity = 1_000_000;
        var subject = new GasLimitBucketThrottle(capacity);
        subject.bucket().useCapacity(capacity / 4);

        assertEquals(3, subject.freeToUsedRatio());
    }

    @Test
    void hasExpectedUsageRatioIfAllFree() {
        final var capacity = 1_000_000;
        var subject = new GasLimitBucketThrottle(capacity);

        assertEquals(Long.MAX_VALUE, subject.freeToUsedRatio());
    }

    @Test
    void rejectsUnsupportedTXGasLimit() {
        assertFalse(subject.allow(BEYOND_CAPACITY, 0));
    }

    @Test
    void withExcessElapsedNanosCompletelyEmptiesBucket() {
        assertTrue(subject.allow(BUCKET_CAPACITY, Long.MAX_VALUE));
        assertEquals(0, subject.bucket().capacityFree());
    }

    @Test
    void withZeroElapsedNanosSimplyAdjustsCapacityFree() {
        assertTrue(subject.allow(BUCKET_CAPACITY / 2, 0L));
        assertEquals(BUCKET_CAPACITY / 2, subject.bucket().capacityFree());
    }

    @Test
    void withZeroElapsedNanosRejectsUnavailableCapacity() {
        assertFalse(subject.allow(BEYOND_CAPACITY, 0L));
        assertEquals(BUCKET_CAPACITY, subject.bucket().capacityFree());
    }

    @Test
    void lastAllowedUseReclaimsCorrectly() {
        assertTrue(subject.allow(BUCKET_CAPACITY / 3, 0L));
        subject.resetLastAllowedUse();
        assertTrue(subject.allow(BUCKET_CAPACITY / 3, 0L));
        subject.reclaimLastAllowedUse();
        assertEquals(BUCKET_CAPACITY - (BUCKET_CAPACITY / 3), subject.bucket().capacityFree());
        subject.reclaimLastAllowedUse();
        assertEquals(BUCKET_CAPACITY - (BUCKET_CAPACITY / 3), subject.bucket().capacityFree());
    }

    @Test
    void lastAllowedUseReclaimsCorrectlyWithFullUsage() {
        assertTrue(subject.allow(BUCKET_CAPACITY / 2, 0L));
        assertFalse(subject.allow(BUCKET_CAPACITY, 0L));
        subject.reclaimLastAllowedUse();
        assertEquals(BUCKET_CAPACITY, subject.bucket().capacityFree());
        subject.reclaimLastAllowedUse();
        assertEquals(BUCKET_CAPACITY, subject.bucket().capacityFree());
    }

    @Test
    void lastAllowedUseResetsCorrectly() {
        assertTrue(subject.allow(BUCKET_CAPACITY / 3, 0L));
        assertTrue(subject.allow(BUCKET_CAPACITY / 3, 0L));
        subject.resetLastAllowedUse();
        subject.reclaimLastAllowedUse();
        assertEquals(
                BUCKET_CAPACITY - ((BUCKET_CAPACITY / 3) * 2), subject.bucket().capacityFree());
    }
}
