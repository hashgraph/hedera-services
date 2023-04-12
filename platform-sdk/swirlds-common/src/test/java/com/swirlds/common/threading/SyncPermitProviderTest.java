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

import static com.swirlds.common.test.AssertionUtils.assertEventuallyTrue;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.threading.locks.locked.MaybeLocked;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class SyncPermitProviderTest {
    /**
     * Verify that permits are acquired and release properly.
     */
    @Test
    void testPermitRelease() {
        final int numPermits = 3;
        final SyncPermitProvider syncPermitProvider = new SyncPermitProvider(numPermits);

        assertEquals(numPermits, syncPermitProvider.getNumAvailable(), "all permits should be available");

        try (final MaybeLocked maybeLocked = syncPermitProvider.tryAcquire()) {
            assertTrue(maybeLocked.isLockAcquired(), "first acquire should succeed");
            assertEquals(
                    numPermits - 1,
                    syncPermitProvider.getNumAvailable(),
                    "one less permit should be available when a permit is acquired");
        }

        assertEquals(
                numPermits,
                syncPermitProvider.getNumAvailable(),
                "all permits should be available after the acquired permit is released");
    }

    /**
     * Verify that once all permits are acquired, further attempts to acquire fail.
     */
    @Test
    void testAllPermitsAcquired() {
        final int numPermits = 9;
        final SyncPermitProvider syncPermitProvider = new SyncPermitProvider(numPermits);

        assertEquals(numPermits, syncPermitProvider.getNumAvailable(), "all permits should be available");

        final List<MaybeLocked> permits = new ArrayList<>(numPermits);

        // Acquire all the permits
        for (int i = 0; i < numPermits; i++) {
            final MaybeLocked maybeLocked = syncPermitProvider.tryAcquire();
            permits.add(maybeLocked);
            assertTrue(maybeLocked.isLockAcquired(), "first acquire should succeed");
            assertEquals(
                    numPermits - i - 1,
                    syncPermitProvider.getNumAvailable(),
                    "one less permit should be available when a permit is acquired");
        }

        // Attempts to acquire more permits should fail
        final MaybeLocked shouldNotAcquire = syncPermitProvider.tryAcquire();
        assertFalse(shouldNotAcquire.isLockAcquired(), "no further permits should be able to be acquired");

        // Releasing permits should result in more permits being available
        for (int i = 0; i < numPermits; i++) {
            final MaybeLocked maybeLocked = permits.get(i);
            maybeLocked.close();
            assertEquals(
                    i + 1,
                    syncPermitProvider.getNumAvailable(),
                    "one more permit should be available when a permit is released");
        }
    }

    @Test
    void testJoin() {
        final int numPermits = 3;
        final SyncPermitProvider syncPermitProvider = new SyncPermitProvider(numPermits);

        final List<MaybeLocked> permits = new ArrayList<>(numPermits);
        // Acquire all the permits
        for (int i = 0; i < numPermits; i++) {
            final MaybeLocked maybeLocked = syncPermitProvider.tryAcquire();
            permits.add(maybeLocked);
            assertTrue(maybeLocked.isLockAcquired());
        }

        // Attempts to acquire more permits should fail
        final MaybeLocked shouldNotAcquire = syncPermitProvider.tryAcquire();
        assertFalse(shouldNotAcquire.isLockAcquired(), "no further permits should be able to be acquired");

        final AtomicBoolean joinCalled = new AtomicBoolean(false);

        // Have a separate thread attempt to join the syncPermitProvider
        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        final Future<Void> future = executorService.submit(() -> {
            syncPermitProvider.join();
            joinCalled.set(true);
            return null;
        });

        try {
            // wait a bit, to give join time to potentially misbehave
            MILLISECONDS.sleep(50);
        } catch (final InterruptedException e) {
            throw new RuntimeException(e);
        }

        assertFalse(joinCalled.get(), "join should not be called until all permits are released");

        // close the permits that have already been acquired, so the join will succeed
        permits.forEach(MaybeLocked::close);

        assertEventuallyTrue(
                joinCalled::get, Duration.ofMillis(1000), "join should be called after all permits are released");
    }
}
