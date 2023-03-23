/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.threading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.threading.locks.locked.MaybeLocked;
import org.junit.jupiter.api.Test;

public class SyncPermitTest {

    /**
     * Verify that permits are acquired and release properly.
     */
    @Test
    void testPermitRelease() {
        final int numPermits = 3;
        final SyncPermit syncPermit = new SyncPermit(numPermits);

        assertEquals(numPermits, syncPermit.getNumAvailable(), "all permits should be available");

        try (final MaybeLocked maybeLocked = syncPermit.tryAcquire()) {
            assertTrue(maybeLocked.isLockAcquired(), "first acquire should succeed");
            assertEquals(
                    numPermits - 1,
                    syncPermit.getNumAvailable(),
                    "one less permit should be available when a permit is acquired");
        }

        assertEquals(
                numPermits,
                syncPermit.getNumAvailable(),
                "all permits should be available after the acquired permit is released");
    }

    /**
     * Verify that once all permits are acquired, further attempts to acquire fail.
     */
    @Test
    void testAllPermitsAcquired() {
        final int numPermits = 2;
        final SyncPermit syncPermit = new SyncPermit(numPermits);

        assertEquals(numPermits, syncPermit.getNumAvailable(), "all permits should be available");

        try (final MaybeLocked maybeLocked1 = syncPermit.tryAcquire()) {
            assertTrue(maybeLocked1.isLockAcquired(), "first acquire should succeed");
            assertEquals(
                    numPermits - 1,
                    syncPermit.getNumAvailable(),
                    "one less permit should be available when a permit is acquired");

            try (final MaybeLocked maybeLocked2 = syncPermit.tryAcquire()) {
                assertTrue(maybeLocked2.isLockAcquired(), "first acquire should succeed");
                assertEquals(
                        numPermits - 2,
                        syncPermit.getNumAvailable(),
                        "two less permits should be available when a permit is acquired");

                try (final MaybeLocked maybeLocked3 = syncPermit.tryAcquire()) {
                    assertFalse(maybeLocked3.isLockAcquired(), "no further permits should be able to be acquired");
                }

                assertEquals(numPermits - 2, syncPermit.getNumAvailable(), "incorrect number of permits remaining");
            }

            assertEquals(numPermits - 1, syncPermit.getNumAvailable(), "incorrect number of permits remaining");
        }

        assertEquals(numPermits, syncPermit.getNumAvailable(), "incorrect number of permits remaining");
    }
}
