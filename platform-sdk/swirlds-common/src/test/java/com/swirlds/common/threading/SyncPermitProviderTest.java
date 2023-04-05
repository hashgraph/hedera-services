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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class SyncPermitProviderTest {

    /**
     * Verify that permits are acquired and release properly.
     */
    @Test
    void testPermitRelease() {
        final int numPermits = 3;
        final SyncPermitProvider syncPermit = new SyncPermitProvider(numPermits);

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
        final int numPermits = 9;
        final SyncPermitProvider syncPermit = new SyncPermitProvider(numPermits);

        assertEquals(numPermits, syncPermit.getNumAvailable(), "all permits should be available");

        final List<MaybeLocked> permits = new ArrayList<>(numPermits);

        // Acquire all the permits
        for (int i = 0; i < numPermits; i++) {
            final MaybeLocked maybeLocked = syncPermit.tryAcquire();
            permits.add(maybeLocked);
            assertTrue(maybeLocked.isLockAcquired(), "first acquire should succeed");
            assertEquals(
                    numPermits - i - 1,
                    syncPermit.getNumAvailable(),
                    "one less permit should be available when a permit is acquired");
        }

        // Attempts to acquire more permits should fail
        final MaybeLocked shouldNotAcquire = syncPermit.tryAcquire();
        assertFalse(shouldNotAcquire.isLockAcquired(), "no further permits should be able to be acquired");

        // Releasing permits should result in more permits being available
        for (int i = 0; i < numPermits; i++) {
            final MaybeLocked maybeLocked = permits.get(i);
            maybeLocked.close();
            assertEquals(
                    i + 1,
                    syncPermit.getNumAvailable(),
                    "one more permit should be available when a permit is released");
        }
    }
}
